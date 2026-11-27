import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("audit_runtime", pathlib.Path(__file__).with_name("audit-runtime.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

A = "pkg:maven/example/a@1"
B = "pkg:maven/example/b@2"
BOM = {"components": [{"purl": A}, {"purl": B}]}


class AuditRuntimeTest(unittest.TestCase):
    def test_clear_requires_all_packages(self):
        report = module.audit(BOM, lambda queries: {"results": [{} for _ in queries]})
        self.assertEqual(report["status"], "clear")
        self.assertEqual(report["packages_checked"], 2)

    def test_findings_and_pagination_are_retained(self):
        def query(queries):
            if len(queries) == 2:
                return {"results": [{"vulns": [{"id": "TEST-1"}], "next_page_token": "next"}, {}]}
            self.assertEqual(queries[0]["package"]["purl"], A)
            self.assertEqual(queries[0]["page_token"], "next")
            return {"results": [{"vulns": [{"id": "TEST-2"}]}]}
        report = module.audit(BOM, query)
        self.assertEqual(report["status"], "findings")
        self.assertEqual([item["id"] for item in report["packages"][0]["vulnerabilities"]], ["TEST-1", "TEST-2"])

    def test_incomplete_or_invalid_response_never_passes(self):
        with self.assertRaises(ValueError):
            module.audit(BOM, lambda queries: {"results": [{}]})
        with self.assertRaises(ValueError):
            module.audit(BOM, lambda queries: {"results": [{"error": "unknown"}, {}]})

    def test_repeated_page_token_fails(self):
        with self.assertRaises(ValueError):
            module.audit({"components": [{"purl": A}]},
                         lambda queries: {"results": [{"next_page_token": "same"}]})

    def test_empty_inventory_fails(self):
        with self.assertRaises(ValueError):
            module.audit({"components": []}, lambda queries: self.fail("Empty inventory must not query OSV"))


if __name__ == "__main__":
    unittest.main()
