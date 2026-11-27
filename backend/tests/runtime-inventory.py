"""Reconcile resolved Maven runtime dependencies with the verified Boot JAR."""
import hashlib
import json
import pathlib
import sys
import zipfile
from urllib.parse import quote


def runtime_components(tree):
    components = {}
    def visit(node):
        for dependency in node.get("children", []):
            if dependency.get("scope") not in ("compile", "runtime"):
                continue
            assert dependency["type"] == "jar", "Unexpected runtime artifact type"
            group, artifact, version = (dependency[key] for key in ("groupId", "artifactId", "version"))
            classifier = dependency.get("classifier", "")
            filename = artifact + "-" + version + ("-" + classifier if classifier else "") + ".jar"
            purl = "pkg:maven/" + quote(group, safe="") + "/" + quote(artifact, safe="") + "@" + quote(version, safe="")
            if classifier:
                purl += "?classifier=" + quote(classifier, safe="")
            component = {"type": "library", "group": group, "name": artifact,
                         "version": version, "purl": purl}
            assert filename not in components or components[filename] == component, "Ambiguous JAR name: " + filename
            components[filename] = component
            visit(dependency)
    visit(tree)
    assert components, "Maven runtime inventory is empty"
    return components


def inventory(tree, jar_path):
    components = runtime_components(tree)
    with zipfile.ZipFile(jar_path) as archive:
        libraries = {pathlib.PurePosixPath(name).name: name for name in archive.namelist()
                     if name.startswith("BOOT-INF/lib/") and name.endswith(".jar")}
        missing = sorted(components.keys() - libraries.keys())
        # Spring Boot adds its own jarmode tools during repackaging.
        extra = sorted(libraries.keys() - components.keys())
        for name in extra:
            prefix = "spring-boot-jarmode-tools-"
            assert name.startswith(prefix), "Unresolved packaged dependency: " + name
            version = name[len(prefix):-4]
            assert version, "Missing jarmode tools version"
            components[name] = {"type": "library", "group": "org.springframework.boot",
                                "name": "spring-boot-jarmode-tools", "version": version,
                                "purl": "pkg:maven/org.springframework.boot/spring-boot-jarmode-tools@" + quote(version, safe="")}
        assert not missing, "Resolved runtime JARs missing from package: " + ", ".join(missing)
        for name, component in components.items():
            component["hashes"] = [{"alg": "SHA-256", "content": hashlib.sha256(archive.read(libraries[name])).hexdigest()}]
        return {"bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1,
                "components": [components[name] for name in sorted(components)]}


def main():
    tree_path, jar_path, output_path = sys.argv[1:]
    report = inventory(json.loads(pathlib.Path(tree_path).read_text(encoding="utf-8")), jar_path)
    pathlib.Path(output_path).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("PASS: " + str(len(report["components"])) + " runtime JARs reconciled with Maven and hashed")


if __name__ == "__main__":
    main()
