classpath "../../lib,../test-src,../src,../build/tosa.jar"
typeloader tosa.loader.DBTypeLoader

var result = org.junit.runner.JUnitCore.runClasses({tosa.loader.DBTypeInfoTest})
print("Ran ${result.RunCount} tests")
print("Had ${result.FailureCount} failures")
for (f in result.Failures) {
  print(f.TestHeader)
  print(f.Trace)
}