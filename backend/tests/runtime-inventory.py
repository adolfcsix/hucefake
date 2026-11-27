"""Reconcile resolved Maven runtime JARs and SHA-256 with the verified Boot JAR."""
import hashlib
import json
import pathlib
import sys
import zipfile
from urllib.parse import quote


def component(group, artifact, version, classifier=""):
    purl = "pkg:maven/" + quote(group, safe="") + "/" + quote(artifact, safe="") + "@" + quote(version, safe="")
    if classifier:
        purl += "?classifier=" + quote(classifier, safe="")
    return {"type": "library", "group": group, "name": artifact, "version": version, "purl": purl}


def runtime_components(root):
    components = {}
    for jar in sorted(root.rglob("*.jar")):
        parts = jar.relative_to(root).parts
        assert len(parts) >= 4, "Invalid Maven repository path: " + str(jar)
        group, artifact, version = ".".join(parts[:-3]), parts[-3], parts[-2]
        prefix = artifact + "-" + version
        stem = jar.stem
        assert stem == prefix or stem.startswith(prefix + "-"), "Invalid Maven artifact filename: " + jar.name
        classifier = stem[len(prefix) + 1:] if stem != prefix else ""
        entry = component(group, artifact, version, classifier)
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        assert jar.name not in components, "Ambiguous resolved JAR name: " + jar.name
        components[jar.name] = (entry, digest, jar)
    assert components, "Maven runtime inventory is empty"
    return components


def metadata_only_starter(entry, jar, components):
    # A starter aggregates dependencies. Do not excuse missing executable code
    # or configuration simply because an artifact has a starter-like name.
    if entry["group"] != "org.springframework.boot":
        return False
    if not (entry["name"] == "spring-boot-starter" or entry["name"].startswith("spring-boot-starter-")):
        return False
    if "spring-boot-" + entry["version"] + ".jar" not in components:
        return False
    if jar.name != entry["name"] + "-" + entry["version"] + ".jar":
        return False
    with zipfile.ZipFile(jar) as archive:
        return all(info.is_dir() or info.filename in {
            "META-INF/MANIFEST.MF", "META-INF/LICENSE", "META-INF/LICENSE.txt",
            "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/DEPENDENCIES",
            "LICENSE", "LICENSE.txt", "NOTICE", "NOTICE.txt"
        } or info.filename in {
            "META-INF/maven/" + entry["group"] + "/" + entry["name"] + "/pom.xml",
            "META-INF/maven/" + entry["group"] + "/" + entry["name"] + "/pom.properties"
        } for info in archive.infolist())


def inventory(root, jar_path):
    components = runtime_components(root)
    with zipfile.ZipFile(jar_path) as archive:
        libraries = {}
        for name in archive.namelist():
            if name.startswith("BOOT-INF/lib/") and name.endswith(".jar"):
                filename = pathlib.PurePosixPath(name).name
                assert filename not in libraries, "Duplicate packaged JAR: " + filename
                libraries[filename] = name
        missing = sorted(components.keys() - libraries.keys())
        result = []
        for name in missing:
            entry, digest, resolved_jar = components[name]
            assert metadata_only_starter(entry, resolved_jar, components), "Resolved runtime JARs missing from package: " + name
            # Keep aggregators in the audit too, but distinguish them from packaged libraries.
            entry["hashes"] = [{"alg": "SHA-256", "content": digest}]
            entry["properties"] = [{"name": "huce:packaging", "value": "resolved-metadata-only-starter"}]
            result.append(entry)
            print("Verified resolved metadata-only starter not packaged: " + name)
        for name in sorted(libraries):
            digest = hashlib.sha256(archive.read(libraries[name])).hexdigest()
            if name in components:
                entry, expected_digest, _ = components[name]
                assert digest == expected_digest, "Packaged JAR differs from resolved dependency: " + name
            else:
                # Boot injects its own jarmode tools during repackage, at the Boot version.
                prefix = "spring-boot-jarmode-tools-"
                assert name.startswith(prefix), "Unresolved packaged dependency: " + name
                version = name[len(prefix):-4]
                assert version and "spring-boot-" + version + ".jar" in components, "Jarmode version does not match Spring Boot"
                entry = component("org.springframework.boot", "spring-boot-jarmode-tools", version)
            entry["hashes"] = [{"alg": "SHA-256", "content": digest}]
            entry["properties"] = [{"name": "huce:packaging", "value": "packaged"}]
            result.append(entry)
        return {"bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1, "components": result}


def main():
    resolved, jar_path, output_path = sys.argv[1:]
    report = inventory(pathlib.Path(resolved), jar_path)
    pathlib.Path(output_path).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("PASS: " + str(len(report["components"])) + " Maven components reconciled (packaged JARs and verified metadata-only starters)")


if __name__ == "__main__":
    main()
