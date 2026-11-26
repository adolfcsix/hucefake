"""Compare the tested backend with the final package and verify every built frontend asset."""
import hashlib
import pathlib
import sys
import zipfile

tested, packaged, dist = sys.argv[1:]
def digest(data):
    return hashlib.sha256(data).hexdigest()

def backend_entries(archive):
    return {
        name: digest(archive.read(name))
        for name in archive.namelist()
        if not name.endswith("/")
        and (name.startswith("BOOT-INF/lib/") or name.startswith("BOOT-INF/classes/"))
        and not name.startswith("BOOT-INF/classes/static/")
    }

with zipfile.ZipFile(tested) as original, zipfile.ZipFile(packaged) as final:
    a, b = backend_entries(original), backend_entries(final)
    assert a and any(n.endswith(".class") for n in a) and any(n.startswith("BOOT-INF/lib/") for n in a)
    diff = sorted(n for n in a.keys() | b.keys() if a.get(n) != b.get(n))
    assert not diff, "Backend class/resource/dependency mismatch: " + ", ".join(diff)
    root = pathlib.Path(dist)
    assets = {
        "BOOT-INF/classes/static/" + f.relative_to(root).as_posix(): digest(f.read_bytes())
        for f in root.rglob("*") if f.is_file()
    }
    assert "BOOT-INF/classes/static/index.html" in assets
    actual = {
        n: digest(final.read(n)) for n in final.namelist()
        if n.startswith("BOOT-INF/classes/static/") and not n.endswith("/")
    }
    assert actual == assets, "Packaged frontend differs from verified Vite output"
    print(f"PASS: {len(a)} backend entries and {len(assets)} frontend assets match verified builds")
