package tosa.loader.parser.mysql;

import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import tosa.loader.data.*;
import tosa.loader.parser.SQLParserConstants;
import tosa.loader.parser.SQLTokenizer;

import javax.management.ObjectName;
import javax.swing.text.StringContent;
import java.awt.*;
import java.awt.image.ImageFilter;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Created by IntelliJ IDEA.
 * User: alan
 * Date: 12/27/10
 * Time: 6:14 PM
 * To change this template use File | Settings | File Templates.
 */
public class MySQL51SQLParser implements SQLParserConstants {

  private SQLTokenizer _tokenizer;

  public DBData parseFile(String fileContents) {
    _tokenizer = new SQLTokenizer(fileContents);
    List<TableData> tables = new ArrayList<TableData>();
    // TODO - AHK - Other Create calls?  Other stuff?  Closing semi-colon?
    TableData table = parseCreate();
    tables.add(table);
    return new DBData(tables);
  }

  private String consumeToken() {
    return _tokenizer.consumeToken();
  }

  private boolean accept(String... tokens) {
    return _tokenizer.acceptIgnoreCase(tokens);
  }

  private boolean peek(String... tokens) {
    return _tokenizer.peekIgnoreCase(tokens);
  }

  private void expect(String expected) {
    _tokenizer.expectIgnoreCase(expected);
  }


  // The grammar for this parser is based on the documentation at http://dev.mysql.com/doc/refman/5.1/en/create-table.html

  //================================== Parsing Methods ========================

  /*CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name
    (create_definition,...)
    [table_options]
    [partition_options]
Or:

CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name
    [(create_definition,...)]
    [table_options]
    [partition_options]
    select_statement
Or:

CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name
    { LIKE old_tbl_name | (LIKE old_tbl_name) }*/
  private TableData parseCreate() {
    if (accept(CREATE)) {
      accept(TEMPORARY); // Discard; we don't care
      expect(TABLE);
      accept(IF, NOT, EXISTS);
      String tableName = consumeToken();
      List<ColumnData> columns = null;
      if (accept(LIKE)) {
        String likeTableName = consumeToken();
        // TODO - AHK - And what do we do with it, exactly?
      } else if (accept(OPEN_PAREN, LIKE)) {
        String likeTableName = consumeToken();
        expect(CLOSE_PAREN);
        // TODO - AHK - And what do we do with it, exactly?
      } else if (accept(OPEN_PAREN)) {
        columns = parseCreateDefinitions();
        expect(CLOSE_PAREN);
        parseTableOptions();
        parsePartitionOptions();
        parseSelectStatement();
      } else {
        // No columns, but there must be a select statement
        parseTableOptions();
        parsePartitionOptions();
        parseSelectStatement();
      }

      expect(SEMI_COLON);

      return new TableData(tableName, columns, null, null);
    } else {
      return null;
    }
  }

  private List<ColumnData> parseCreateDefinitions() {
    // TODO - AHK - Is it legal to have 0 columns?
    List<ColumnData> columns = new ArrayList<ColumnData>();
    ColumnData columnData = parseCreateDefinition();
    if (columnData != null) {
      columns.add(columnData);
    }
    while (accept(COMMA)) {
      columnData = parseCreateDefinition();
      if (columnData != null) {
        columns.add(columnData);
      }
    }
    return columns;
  }

  /*create_definition:
    col_name column_definition
  | [CONSTRAINT [symbol]] PRIMARY KEY [index_type] (index_col_name,...)
      [index_option] ...
  | {INDEX|KEY} [index_name] [index_type] (index_col_name,...)
      [index_option] ...
  | [CONSTRAINT [symbol]] UNIQUE [INDEX|KEY]
      [index_name] [index_type] (index_col_name,...)
      [index_option] ...
  | {FULLTEXT|SPATIAL} [INDEX|KEY] [index_name] (index_col_name,...)
      [index_option] ...
  | [CONSTRAINT [symbol]] FOREIGN KEY
      [index_name] (index_col_name,...) reference_definition
  | CHECK (expr)*/
  private ColumnData parseCreateDefinition() {
    if (accept(CONSTRAINT)) {
      String symbolName;
      if (peek(PRIMARY, KEY) || peek(UNIQUE) || peek(FOREIGN, KEY)) {
        symbolName = null;
      } else {
        symbolName = consumeToken();
      }
    }

    if (accept(PRIMARY, KEY)) {
      parseIndexType();
      parseIndexColumnNames();
      parseIndexOptions();
    } else if (accept(INDEX) || accept(KEY)) {
      parseIndexName();
      parseIndexType();
      parseIndexColumnNames();
      parseIndexOptions();
    } else if (accept(UNIQUE)) {
      // TODO - AHK
      if (!accept(INDEX)) {
        expect(KEY);
      }
      parseIndexName();
      parseIndexType();
      parseIndexColumnNames();
      parseIndexOptions();
    } else if (accept(FULLTEXT) || accept(SPATIAL)) {
      if (!accept(INDEX)) {
        expect(KEY);
      }
      parseIndexName();
      parseIndexColumnNames();
      parseIndexOptions();
    } else if (accept(FOREIGN, KEY)) {
      parseIndexName();
      parseIndexColumnNames();
      parseReferenceDefinition();
    } else if (accept(CHECK)) {
      parseParenthesizedExpression();
    } else {
      return parseColumnDefinition();
    }

    return null;
  }

  private void parseIndexName() {
    if (!(peek(USING) || peek(OPEN_PAREN))) {
      String name = consumeToken();
    }
  }

  private void parseIndexType() {
    if (accept(USING)) {
      if (!accept(BTREE)) {
        expect(HASH);
      }
    }
  }

  private void parseIndexOptions() {
    if (accept(KEY_BLOCK_SIZE)) {
      accept(EQUALS);
      String value = consumeToken();
      parseIndexOptions();
    } else if (accept(USING)) {
      // TODO - AHK - Sould there be an expect() variant for an OR situation like this?
      if (!accept(BTREE)) {
        expect(HASH);
      }
      parseIndexOptions();
    } else if (accept(WITH, PARSER)) {
      String parserName = consumeToken();
    }
  }

  private void parseReferenceDefinition() {
    if (accept(REFERENCES)) {
      String tableName = consumeToken();
      expect(OPEN_PAREN);
      String columnName = consumeToken();
      while (accept(COMMA)) {
        columnName = consumeToken();
      }
      expect(CLOSE_PAREN);
      if (accept(MATCH, FULL) || accept(MATCH, PARTIAL) || accept(MATCH, SIMPLE)) {
        // Just eat it
      }
      if (accept(ON, DELETE)) {
        parseReferenceOption();
      }
      if (accept(ON, UPDATE)) {
        parseReferenceOption();
      }
    }
  }

  private void parseReferenceOption() {
    if (accept(RESTRICT) || accept(CASCADE) || accept(SET, NULL) || accept(NO, ACTION)) {
      // Just eat it
    } else {
      // Error case
    }
  }

  private void parseIndexColumnNames() {
    expect(OPEN_PAREN);
    parseIndexColName();
    while (accept(COMMA)) {
      parseIndexColName();
    }
    expect(CLOSE_PAREN);
  }

  private void parseIndexColName() {
    String columnName = consumeToken();
    if (accept(OPEN_PAREN)) {
      String length = consumeToken();
      expect(CLOSE_PAREN);
    }
    if (accept(ASC)) {
      // Just eat it
    } else if (accept(DESC)) {
      // Just eat it
    }
  }

  /*column_definition:
    data_type [NOT NULL | NULL] [DEFAULT default_value]
      [AUTO_INCREMENT] [UNIQUE [KEY] | [PRIMARY] KEY]
      [COMMENT 'string']
      [COLUMN_FORMAT {FIXED|DYNAMIC|DEFAULT}]
      [STORAGE {DISK|MEMORY|DEFAULT}]
      [reference_definition]*/
  private ColumnData parseColumnDefinition() {
    // Note:  In the syntax defined in the MySQL docs they don't include the name as part of the column_definition
    // production, but I'm moving it in there for the sake of sanity
    String name = consumeToken();
    ColumnType columnType = parseDataType();
    if (accept(NOT, NULL)) {

    } else if (accept(NULL)) {

    }

    if (accept(DEFAULT)) {
      String defaultValue = consumeToken();
    }

    accept(AUTO_INCREMENT);
    if (accept(UNIQUE)) {
      accept(KEY);
    } else if (accept(PRIMARY)) {
      expect(KEY);
    } else if (accept(KEY)) {

    }

    if (accept(COMMENT)) {
      String comment = parseQuotedString();
    }

    if (accept(COLUMN_FORMAT)) {
      if (accept(FIXED) || accept(DYNAMIC) || accept(DEFAULT)) {

      } else {
        // TODO - AHK - Error case
      }
    }

    if (accept(STORAGE)) {
      if (accept(DISK) || accept(MEMORY) || accept(DEFAULT)) {

      } else {
        // TODO - AHK - Error case
      }
    }

    parseReferenceDefinition();

    // TODO - AHK
    return new ColumnData(name, columnType);
  }

  /*
  data_type:
    BIT[(length)]
  | TINYINT[(length)] [UNSIGNED] [ZEROFILL]
  | SMALLINT[(length)] [UNSIGNED] [ZEROFILL]
  | MEDIUMINT[(length)] [UNSIGNED] [ZEROFILL]
  | INT[(length)] [UNSIGNED] [ZEROFILL]
  | INTEGER[(length)] [UNSIGNED] [ZEROFILL]
  | BIGINT[(length)] [UNSIGNED] [ZEROFILL]
  | REAL[(length,decimals)] [UNSIGNED] [ZEROFILL]
  | DOUBLE[(length,decimals)] [UNSIGNED] [ZEROFILL]
  | FLOAT[(length,decimals)] [UNSIGNED] [ZEROFILL]
  | DECIMAL[(length[,decimals])] [UNSIGNED] [ZEROFILL]
  | NUMERIC[(length[,decimals])] [UNSIGNED] [ZEROFILL]
  | DATE
  | TIME
  | TIMESTAMP
  | DATETIME
  | YEAR
  | CHAR[(length)]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | VARCHAR(length)
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | BINARY[(length)]
  | VARBINARY(length)
  | TINYBLOB
  | BLOB
  | MEDIUMBLOB
  | LONGBLOB
  | TINYTEXT [BINARY]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | TEXT [BINARY]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | MEDIUMTEXT [BINARY]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | LONGTEXT [BINARY]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | ENUM(value1,value2,value3,...)
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | SET(value1,value2,value3,...)
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | spatial_type
   */
  private ColumnType parseDataType() {
    // TODO - Handle Serial
    if (accept(BIT)) {
      Integer length = parseLength();
      if (length == null || length == 1) {
        return new ColumnType(Types.BIT, BIT, ColumnType.BOOLEAN_ITYPE);
      } else {
        // TODO - AHK
      }
    } else if (accept(TINYINT)) {
      Integer length = parseLength();
      boolean signed = parseNumericModifiers();
      if (length != null && length == 1) {
        // Treat TINYINT(1) as a boolean type
        return new ColumnType(Types.TINYINT, TINYINT, ColumnType.BOOLEAN_ITYPE);
      } else if (signed) {
        return new ColumnType(Types.TINYINT, TINYINT, ColumnType.BYTE_ITYPE);
      } else {
        // TODO - AHK
      }
      // TODO - AHK
    } else if (accept(BOOL) || accept(BOOLEAN)) {

    } else if (accept(SMALLINT)) {
      Integer length = parseLength();
      boolean signed = parseNumericModifiers();
      // TODO - AHK
    } else if (accept(MEDIUMINT)) {
      Integer length = parseLength();
      boolean signed = parseNumericModifiers();
      // TODO - AHK
    } else if (accept(INT)) {
      Integer length = parseLength();
      boolean signed = parseNumericModifiers();
      // TODO - AHK
    } else if (accept(INTEGER)) {
      Integer length = parseLength();
      boolean signed = parseNumericModifiers();
      // TODO - AHK
    } else if (accept(BIGINT)) {
      Integer length = parseLength();
      boolean signed = parseNumericModifiers();
      // TODO - AHK
    } else if (accept(REAL)) {
      parseLengthAndDecimals();
      boolean signed = parseNumericModifiers();
      // TODO - AHK
    } else if (accept(DOUBLE)) {
      parseLengthAndDecimals();
      boolean signed = parseNumericModifiers();
      // TODO - AHK
    } else if (accept(FLOAT)) {
      parseLengthAndDecimals();
      accept(UNSIGNED);
      accept(ZEROFILL);
      // TODO - AHK
    } else if (accept(DECIMAL)) {
      parseLengthAndDecimals();
      accept(UNSIGNED);
      accept(ZEROFILL);
      // TODO - AHK
    } else if (accept(NUMERIC)) {
      parseLengthAndDecimals();
      accept(UNSIGNED);
      accept(ZEROFILL);
      // TODO - AHK
    } else if (accept(DATE)) {
      // TODO - AHK
    } else if (accept(TIME)) {
      // TODO - AHK
    } else if (accept(TIMESTAMP)) {
      // TODO - AHK
    } else if (accept(DATETIME)) {
      // TODO - AHK
    } else if (accept(YEAR)) {
      // TODO - AHK
    } else if (accept(CHAR)) {
      Integer length = parseLength();
      String charSetName = parseCharSet();
      String collation = parseCollation();
      // TODO - AHK
    } else if (accept(VARCHAR)) {
      Integer length = parseLength();
      String charSetName = parseCharSet();
      String collation = parseCollation();
      // TODO - AHK
    } else if (accept(BINARY)) {
      Integer length = parseLength();
      // TODO - AHK
    } else if (accept(VARBINARY)) {
      Integer length = parseLength();
      // TODO - AHK
    } else if (accept(TINYBLOB)) {

    } else if (accept(BLOB)) {

    } else if (accept(MEDIUMBLOB)) {

    } else if (accept(LONGBLOB)) {

    } else if (accept(TINYTEXT)) {
      accept(BINARY);
      String charSetName = parseCharSet();
      String collation = parseCollation();
    } else if (accept(TEXT)) {
      accept(BINARY);
      String charSetName = parseCharSet();
      String collation = parseCollation();
    } else if (accept(MEDIUMTEXT)) {
      accept(BINARY);
      String charSetName = parseCharSet();
      String collation = parseCollation();
    } else if (accept(LONGTEXT)) {
      accept(BINARY);
      String charSetName = parseCharSet();
      String collation = parseCollation();
    } else if (accept(ENUM)) {
      List<String> values = parseEnumOrSetValueList();
      String charSetName = parseCharSet();
      String collation = parseCollation();
    } else if (accept(SET)) {
      List<String> values = parseEnumOrSetValueList();
      String charSetName = parseCharSet();
      String collation = parseCollation();
    }
    return null;
  }

  // TODO - AHK - Maybe return an int instead?
  private Integer parseLength() {
    if (accept(OPEN_PAREN)) {
      String length = consumeToken();
      expect(CLOSE_PAREN);
      return Integer.valueOf(length);
    } else {
      return null;
    }
  }

  private boolean parseNumericModifiers() {
    boolean signed = true;
    if (accept(SIGNED)) {
      signed = true;
    } else if (accept(UNSIGNED)) {
      signed = false;
    }

    // Zerofill columns are automatically treated as unsigned
    if (accept(ZEROFILL)) {
      signed = false;
    }

    return signed;
  }

  private void parseLengthAndDecimals() {
    // TODO - AHK - Sometimes the comma isn't optional, but I don't think that matters here
    if (accept(OPEN_PAREN)) {
      String length = consumeToken();
      if (accept(COMMA)) {
        String decimals = consumeToken();
      }
      expect(CLOSE_PAREN);
    }
  }

  private String parseCharSet() {
    if (accept(CHARACTER, SET)) {
      return consumeToken();
    } else {
      return null;
    }
  }

  private String parseCollation() {
    if (accept(COLLATE)) {
      return consumeToken();
    } else {
      return null;
    }
  }

  private List<String> parseEnumOrSetValueList() {
    List<String> values = new ArrayList<String>();
    expect(OPEN_PAREN);
    values.add(consumeToken());
    while(accept(COMMA)) {
      values.add(consumeToken());
    }
    expect(CLOSE_PAREN);
    return values;
  }

  /*table_options:
    table_option [[,] table_option] ...*/
  private void parseTableOptions() {
    parseTableOption();
    // TODO - AHK - Are the commas required?  If not, we'll need to see if a table option was
    // actually parsed or not
    while (accept(COMMA)) {
      parseTableOption();
    }
  }

  /*
  table_option:
    ENGINE [=] engine_name
  | AUTO_INCREMENT [=] value
  | AVG_ROW_LENGTH [=] value
  | [DEFAULT] CHARACTER SET [=] charset_name
  | CHECKSUM [=] {0 | 1}
  | [DEFAULT] COLLATE [=] collation_name
  | COMMENT [=] 'string'
  | CONNECTION [=] 'connect_string'
  | DATA DIRECTORY [=] 'absolute path to directory'
  | DELAY_KEY_WRITE [=] {0 | 1}
  | INDEX DIRECTORY [=] 'absolute path to directory'
  | INSERT_METHOD [=] { NO | FIRST | LAST }
  | KEY_BLOCK_SIZE [=] value
  | MAX_ROWS [=] value
  | MIN_ROWS [=] value
  | PACK_KEYS [=] {0 | 1 | DEFAULT}
  | PASSWORD [=] 'string'
  | ROW_FORMAT [=] {DEFAULT|DYNAMIC|FIXED|COMPRESSED|REDUNDANT|COMPACT}
  | TABLESPACE tablespace_name [STORAGE {DISK|MEMORY|DEFAULT}]
  | UNION [=] (tbl_name[,tbl_name]...)
  */
  private void parseTableOption() {
    if (accept(ENGINE)) {
      accept(EQUALS);
      String engineName = consumeToken();
    } else if (accept(AUTO_INCREMENT)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(AVG_ROW_LENGTH)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(DEFAULT, CHARACTER, SET) || accept(CHARACTER, SET)) {
      accept(EQUALS);
      String charsetName = consumeToken();
    } else if (accept(CHECKSUM)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(DEFAULT, COLLATE) || accept(COLLATE)) {
      accept(EQUALS);
      String collationName = consumeToken();
    } else if (accept(COMMENT)) {
      accept(EQUALS);
      String comment = consumeToken();
    } else if (accept(CONNECTION)) {
      accept(EQUALS);
      String connection = consumeToken();
    } else if (accept(DATA, DICTIONARY)) {
      accept(EQUALS);
      String path = consumeToken();
    } else if (accept(DELAY_KEY_WRITE)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(INDEX, DIRECTORY)) {
      accept(EQUALS);
      String path = consumeToken();
    } else if (accept(INSERT_METHOD)) {
      accept(EQUALS);
      String method = consumeToken();
    } else if (accept(KEY_BLOCK_SIZE)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(MAX_ROWS)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(MIN_ROWS)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(PACK_KEYS)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(PASSWORD)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(ROW_FORMAT)) {
      accept(EQUALS);
      String value = consumeToken();
    } else if (accept(TABLESPACE)) {
      String tablespaceName = consumeToken();
      if (accept(STORAGE)) {
        // TODO - AHK - Do we care if it's one of the correct tokens?
        consumeToken();
      }
    } else if (accept(UNION)) {
      String unionName = consumeToken();
      while (accept(COMMA)) {
        unionName = consumeToken();
      }
    }
  }

  /*
  partition_options:
    PARTITION BY
        { [LINEAR] HASH(expr)
        | [LINEAR] KEY(column_list)
        | RANGE(expr)
        | LIST(expr) }
    [PARTITIONS num]
    [SUBPARTITION BY
        { [LINEAR] HASH(expr)
        | [LINEAR] KEY(column_list) }
      [SUBPARTITIONS num]
    ]
    [(partition_definition [, partition_definition] ...)]
   */
  private void parsePartitionOptions() {
    if (accept(PARTITION, BY)) {
      if (accept(LINEAR, HASH) || accept(HASH)) {
        parseParenthesizedExpression();
      } else if (accept(KEY) || accept(LINEAR, KEY)) {
        parseParenthesizedExpression();
      } else if (accept(RANGE)) {
        parseParenthesizedExpression();
      } else if (accept(LIST)) {
        parseParenthesizedExpression();
      } else {
        // TODO - AHK - Error case?
      }

      if (accept(PARTITIONS)) {
        String num = consumeToken();
      }

      if (accept(SUBPARTITION, BY)) {
        if (accept(HASH) || accept(LINEAR, HASH)) {
          parseParenthesizedExpression();
        } else if (accept(KEY) || accept(LINEAR, KEY)) {
          parseParenthesizedExpression();
        } else {
          // TODO - AHK - Error case?
        }

        if (accept(SUBPARTITIONS)) {
          String num = consumeToken();
        }
      }

      parsePartitionDefinition();
      while (accept(COMMA)) {
        parsePartitionDefinition();
      }
    }
  }

  private void parseParenthesizedExpression() {
    expect(OPEN_PAREN);
    // TODO - AHK - Deal with escaping and such at some point, but for now just
    // eat tokens until we hit the closing )
    while (!_tokenizer.token().equalsIgnoreCase(CLOSE_PAREN)) {
      consumeToken();
    }
    expect(CLOSE_PAREN);
  }

  /*
  partition_definition:
    PARTITION partition_name
        [VALUES
            {LESS THAN {(expr) | MAXVALUE}
            |
            IN (value_list)}]
        [[STORAGE] ENGINE [=] engine_name]
        [COMMENT [=] 'comment_text' ]
        [DATA DIRECTORY [=] 'data_dir']
        [INDEX DIRECTORY [=] 'index_dir']
        [MAX_ROWS [=] max_number_of_rows]
        [MIN_ROWS [=] min_number_of_rows]
        [TABLESPACE [=] tablespace_name]
        [NODEGROUP [=] node_group_id]
        [(subpartition_definition [, subpartition_definition] ...)]
   */
  private void parsePartitionDefinition() {
    if (accept(PARTITION)) {
      String partitionName = consumeToken();
      if (accept(VALUES)) {
        if (accept(LESS, THAN)) {
          if (accept(MAXVALUE)) {
            // Nothing to do
          } else {
            parseParenthesizedExpression();
          }
        } else if (accept(IN)) {
          expect(OPEN_PAREN);
          parseValueList();
          expect(CLOSE_PAREN);
        } else {
          // TODO - AHK - Error case?
        }
      }

      if (accept(ENGINE) || accept(STORAGE, ENGINE)) {
        accept(EQUALS);
        String engineName = consumeToken();
      }

      if (accept(COMMENT)) {
        accept(EQUALS);
        String commentText = parseQuotedString();
      }

      if (accept(DATA, DIRECTORY)) {
        accept(EQUALS);
        String dataDir = parseQuotedString();
      }

      if (accept(INDEX, DIRECTORY)) {
        accept(EQUALS);
        String indexDir = parseQuotedString();
      }

      if (accept(MAX_ROWS)) {
        accept(EQUALS);
        String value = consumeToken();
      }

      if (accept(MIN_ROWS)) {
        accept(EQUALS);
        String value = consumeToken();
      }

      if (accept(TABLESPACE)) {
        accept(EQUALS);
        String tablespaceName = consumeToken();
      }

      if (accept(NODEGROUP)) {
        accept(EQUALS);
        String nodeGroupID = consumeToken();
      }

      parseSubpartitionDefinition();
      while(accept(COMMA)) {
        parseSubpartitionDefinition();
      }
    }
  }

  private void parseValueList() {
    // TODO - AHK
  }

  private String parseQuotedString() {
    // TODO - AHK
    return null;
  }

  /*
  subpartition_definition:
    SUBPARTITION logical_name
        [[STORAGE] ENGINE [=] engine_name]
        [COMMENT [=] 'comment_text' ]
        [DATA DIRECTORY [=] 'data_dir']
        [INDEX DIRECTORY [=] 'index_dir']
        [MAX_ROWS [=] max_number_of_rows]
        [MIN_ROWS [=] min_number_of_rows]
        [TABLESPACE [=] tablespace_name]
        [NODEGROUP [=] node_group_id]
   */
  private void parseSubpartitionDefinition() {
    if (accept(SUBPARTITION)) {
      String logicalName = consumeToken();

      if (accept(ENGINE) || accept(STORAGE, ENGINE)) {
        accept(EQUALS);
        String engineName = consumeToken();
      }

      if (accept(COMMENT)) {
        accept(EQUALS);
        String commentText = parseQuotedString();
      }

      if (accept(DATA, DIRECTORY)) {
        accept(EQUALS);
        String dataDir = parseQuotedString();
      }

      if (accept(INDEX, DIRECTORY)) {
        accept(EQUALS);
        String indexDir = parseQuotedString();
      }

      if (accept(MAX_ROWS)) {
        accept(EQUALS);
        String value = consumeToken();
      }

      if (accept(MIN_ROWS)) {
        accept(EQUALS);
        String value = consumeToken();
      }

      if (accept(TABLESPACE)) {
        accept(EQUALS);
        String tablespaceName = consumeToken();
      }

      if (accept(NODEGROUP)) {
        accept(EQUALS);
        String nodeGroupID = consumeToken();
      }
    }
  }

  private void parseSelectStatement() {
    // TODO - AHK
  }

  /*CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name
    (create_definition,...)
    [table_options]
    [partition_options]
Or:

CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name
    [(create_definition,...)]
    [table_options]
    [partition_options]
    select_statement
Or:

CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name
    { LIKE old_tbl_name | (LIKE old_tbl_name) }
create_definition:
    col_name column_definition
  | [CONSTRAINT [symbol]] PRIMARY KEY [index_type] (index_col_name,...)
      [index_option] ...
  | {INDEX|KEY} [index_name] [index_type] (index_col_name,...)
      [index_option] ...
  | [CONSTRAINT [symbol]] UNIQUE [INDEX|KEY]
      [index_name] [index_type] (index_col_name,...)
      [index_option] ...
  | {FULLTEXT|SPATIAL} [INDEX|KEY] [index_name] (index_col_name,...)
      [index_option] ...
  | [CONSTRAINT [symbol]] FOREIGN KEY
      [index_name] (index_col_name,...) reference_definition
  | CHECK (expr)

column_definition:
    data_type [NOT NULL | NULL] [DEFAULT default_value]
      [AUTO_INCREMENT] [UNIQUE [KEY] | [PRIMARY] KEY]
      [COMMENT 'string']
      [COLUMN_FORMAT {FIXED|DYNAMIC|DEFAULT}]
      [STORAGE {DISK|MEMORY|DEFAULT}]
      [reference_definition]

data_type:
    BIT[(length)]
  | TINYINT[(length)] [UNSIGNED] [ZEROFILL]
  | SMALLINT[(length)] [UNSIGNED] [ZEROFILL]
  | MEDIUMINT[(length)] [UNSIGNED] [ZEROFILL]
  | INT[(length)] [UNSIGNED] [ZEROFILL]
  | INTEGER[(length)] [UNSIGNED] [ZEROFILL]
  | BIGINT[(length)] [UNSIGNED] [ZEROFILL]
  | REAL[(length,decimals)] [UNSIGNED] [ZEROFILL]
  | DOUBLE[(length,decimals)] [UNSIGNED] [ZEROFILL]
  | FLOAT[(length,decimals)] [UNSIGNED] [ZEROFILL]
  | DECIMAL[(length[,decimals])] [UNSIGNED] [ZEROFILL]
  | NUMERIC[(length[,decimals])] [UNSIGNED] [ZEROFILL]
  | DATE
  | TIME
  | TIMESTAMP
  | DATETIME
  | YEAR
  | CHAR[(length)]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | VARCHAR(length)
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | BINARY[(length)]
  | VARBINARY(length)
  | TINYBLOB
  | BLOB
  | MEDIUMBLOB
  | LONGBLOB
  | TINYTEXT [BINARY]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | TEXT [BINARY]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | MEDIUMTEXT [BINARY]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | LONGTEXT [BINARY]
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | ENUM(value1,value2,value3,...)
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | SET(value1,value2,value3,...)
      [CHARACTER SET charset_name] [COLLATE collation_name]
  | spatial_type

index_col_name:
    col_name [(length)] [ASC | DESC]

index_type:
    USING {BTREE | HASH}

index_option:
    KEY_BLOCK_SIZE [=] value
  | index_type
  | WITH PARSER parser_name

reference_definition:
    REFERENCES tbl_name (index_col_name,...)
      [MATCH FULL | MATCH PARTIAL | MATCH SIMPLE]
      [ON DELETE reference_option]
      [ON UPDATE reference_option]

reference_option:
    RESTRICT | CASCADE | SET NULL | NO ACTION

table_options:
    table_option [[,] table_option] ...

table_option:
    ENGINE [=] engine_name
  | AUTO_INCREMENT [=] value
  | AVG_ROW_LENGTH [=] value
  | [DEFAULT] CHARACTER SET [=] charset_name
  | CHECKSUM [=] {0 | 1}
  | [DEFAULT] COLLATE [=] collation_name
  | COMMENT [=] 'string'
  | CONNECTION [=] 'connect_string'
  | DATA DIRECTORY [=] 'absolute path to directory'
  | DELAY_KEY_WRITE [=] {0 | 1}
  | INDEX DIRECTORY [=] 'absolute path to directory'
  | INSERT_METHOD [=] { NO | FIRST | LAST }
  | KEY_BLOCK_SIZE [=] value
  | MAX_ROWS [=] value
  | MIN_ROWS [=] value
  | PACK_KEYS [=] {0 | 1 | DEFAULT}
  | PASSWORD [=] 'string'
  | ROW_FORMAT [=] {DEFAULT|DYNAMIC|FIXED|COMPRESSED|REDUNDANT|COMPACT}
  | TABLESPACE tablespace_name [STORAGE {DISK|MEMORY|DEFAULT}]
  | UNION [=] (tbl_name[,tbl_name]...)

partition_options:
    PARTITION BY
        { [LINEAR] HASH(expr)
        | [LINEAR] KEY(column_list)
        | RANGE(expr)
        | LIST(expr) }
    [PARTITIONS num]
    [SUBPARTITION BY
        { [LINEAR] HASH(expr)
        | [LINEAR] KEY(column_list) }
      [SUBPARTITIONS num]
    ]
    [(partition_definition [, partition_definition] ...)]

partition_definition:
    PARTITION partition_name
        [VALUES
            {LESS THAN {(expr) | MAXVALUE}
            |
            IN (value_list)}]
        [[STORAGE] ENGINE [=] engine_name]
        [COMMENT [=] 'comment_text' ]
        [DATA DIRECTORY [=] 'data_dir']
        [INDEX DIRECTORY [=] 'index_dir']
        [MAX_ROWS [=] max_number_of_rows]
        [MIN_ROWS [=] min_number_of_rows]
        [TABLESPACE [=] tablespace_name]
        [NODEGROUP [=] node_group_id]
        [(subpartition_definition [, subpartition_definition] ...)]

subpartition_definition:
    SUBPARTITION logical_name
        [[STORAGE] ENGINE [=] engine_name]
        [COMMENT [=] 'comment_text' ]
        [DATA DIRECTORY [=] 'data_dir']
        [INDEX DIRECTORY [=] 'index_dir']
        [MAX_ROWS [=] max_number_of_rows]
        [MIN_ROWS [=] min_number_of_rows]
        [TABLESPACE [=] tablespace_name]
        [NODEGROUP [=] node_group_id]

select_statement:
    [IGNORE | REPLACE] [AS] SELECT ...   (Some legal select statement)*/
}
