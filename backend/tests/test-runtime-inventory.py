import importlib.util
import pathlib
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("runtime_inventory", pathlib.Path(__file__).with_name("runtime-inventory.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RuntimeInventoryTest(unittest.TestCase):
    def test_transitive_components_and_hashes(self):
        tree = {"children": [{"groupId": "example", "artifactId": "main", "version": "1",
                             "scope": "compile", "type": "jar", "children": [
                                 {"groupId": "example", "artifactId": "transitive", "version": "2",
                                  "scope": "runtime", "type": "jar"}]},
                             {"groupId": "example", "artifactId": "test", "version": "1",
                              "scope": "test", "type": "jar"}]}
        with tempfile.TemporaryDirectory() as folder:
            jar = pathlib.Path(folder) / "app.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("BOOT-INF/lib/main-1.jar", b"main")
                archive.writestr("BOOT-INF/lib/transitive-2.jar", b"transitive")
                archive.writestr("BOOT-INF/lib/spring-boot-jarmode-tools-3.5.8.jar", b"tools")
            result = module.inventory(tree, jar)
            self.assertEqual(len(result["components"]), 3)
            self.assertTrue(all(len(c["hashes"][0]["content"]) == 64 for c in result["components"]))
            with zipfile.ZipFile(jar, "a") as archive:
                archive.writestr("BOOT-INF/lib/unknown-1.jar", b"unknown")
            with self.assertRaises(AssertionError):
                module.inventory(tree, jar)

    def test_missing_runtime_dependency_fails(self):
        tree = {"children": [{"groupId": "example", "artifactId": "main", "version": "1",
                             "scope": "compile", "type": "jar"}]}
        with tempfile.TemporaryDirectory() as folder:
            jar = pathlib.Path(folder) / "app.jar"
            with zipfile.ZipFile(jar, "w"):
                pass
            with self.assertRaises(AssertionError):
                module.inventory(tree, jar)


if __name__ == "__main__":
    unittest.main()
