import importlib.util
import pathlib
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("runtime_inventory", pathlib.Path(__file__).with_name("runtime-inventory.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RuntimeInventoryTest(unittest.TestCase):
    def resolved(self, root, group, artifact, version, data, classifier=""):
        directory = root.joinpath(*group.split("."), artifact, version)
        directory.mkdir(parents=True, exist_ok=True)
        filename = artifact + "-" + version + ("-" + classifier if classifier else "") + ".jar"
        (directory / filename).write_bytes(data)
        return filename

    def test_resolved_components_and_hashes(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder) / "resolved"
            jar = pathlib.Path(folder) / "app.jar"
            files = {
                self.resolved(root, "org.springframework", "spring-core", "6.2.14", b"core"): b"core",
                self.resolved(root, "org.springframework.boot", "spring-boot", "3.5.8", b"boot"): b"boot",
                self.resolved(root, "example", "transitive", "2", b"transitive", "linux"): b"transitive",
                "spring-boot-jarmode-tools-3.5.8.jar": b"tools",
            }
            with zipfile.ZipFile(jar, "w") as archive:
                for filename, data in files.items():
                    archive.writestr("BOOT-INF/lib/" + filename, data)
            result = module.inventory(root, jar)
            self.assertEqual(len(result["components"]), 4)
            purls = {c["purl"] for c in result["components"]}
            self.assertIn("pkg:maven/org.springframework/spring-core@6.2.14", purls)
            self.assertIn("pkg:maven/example/transitive@2?classifier=linux", purls)
            self.assertTrue(all(len(c["hashes"][0]["content"]) == 64 for c in result["components"]))
            with zipfile.ZipFile(jar, "a") as archive:
                archive.writestr("BOOT-INF/lib/unknown-1.jar", b"unknown")
            with self.assertRaisesRegex(AssertionError, "Unresolved packaged dependency"):
                module.inventory(root, jar)

    def test_missing_runtime_dependency_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder) / "resolved"
            self.resolved(root, "example", "main", "1", b"original")
            jar = pathlib.Path(folder) / "app.jar"
            with zipfile.ZipFile(jar, "w"):
                pass
            with self.assertRaisesRegex(AssertionError, "missing from package"):
                module.inventory(root, jar)

    def test_changed_runtime_bytes_fail(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder) / "resolved"
            filename = self.resolved(root, "example", "main", "1", b"original")
            jar = pathlib.Path(folder) / "app.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("BOOT-INF/lib/" + filename, b"changed")
            with self.assertRaisesRegex(AssertionError, "differs from resolved dependency"):
                module.inventory(root, jar)


if __name__ == "__main__":
    unittest.main()
