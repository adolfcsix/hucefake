"""Query OSV for every exact Maven version in the packaged runtime inventory."""
import datetime
import json
import pathlib
import sys
import urllib.request

OSV_URL = "https://api.osv.dev/v1/querybatch"


def query_osv(queries):
    request = urllib.request.Request(
        OSV_URL, data=json.dumps({"queries": queries}).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def audit(bom, query=query_osv):
    components = bom["components"]
    purls = sorted({component["purl"] for component in components})
    if not purls or any(not purl.startswith("pkg:maven/") or "@" not in purl for purl in purls):
        raise ValueError("Expected a nonempty versioned Maven inventory")
    findings = {purl: {} for purl in purls}
    pending = [(purl, {"package": {"purl": purl}}) for purl in purls]
    seen_pages = set()
    while pending:
        batch, pending = pending[:100], pending[100:]
        response = query([item[1] for item in batch])
        results = response["results"]
        if not isinstance(results, list) or len(results) != len(batch):
            raise ValueError("Incomplete OSV batch response")
        for (purl, request), result in zip(batch, results):
            if not isinstance(result, dict) or set(result) - {"vulns", "next_page_token"}:
                raise ValueError("Unexpected OSV result")
            for vulnerability in result.get("vulns", []):
                findings[purl][vulnerability["id"]] = vulnerability
            token = result.get("next_page_token")
            if token:
                marker = (purl, token)
                if marker in seen_pages:
                    raise ValueError("Repeated OSV pagination token")
                seen_pages.add(marker)
                pending.append((purl, {**request, "page_token": token}))
    return {
        "status": "findings" if any(findings.values()) else "clear",
        "checked_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "source": OSV_URL,
        "packages_checked": len(purls),
        "packages": [{"purl": purl, "vulnerabilities": sorted(findings[purl].values(), key=lambda item: item["id"])}
                     for purl in purls],
    }


def main():
    bom_path, report_path = map(pathlib.Path, sys.argv[1:])
    try:
        report = audit(json.loads(bom_path.read_text(encoding="utf-8")))
    except Exception as error:
        report = {"status": "error", "error_type": type(error).__name__, "message": str(error)}
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if report["status"] == "error":
        print("OSV audit incomplete: " + report["error_type"], file=sys.stderr)
        return 2
    print("OSV checked " + str(report["packages_checked"]) + " exact Maven packages")
    for package in report["packages"]:
        for vulnerability in package["vulnerabilities"]:
            print(package["purl"] + " " + vulnerability["id"])
    return 1 if report["status"] == "findings" else 0


if __name__ == "__main__":
    sys.exit(main())
