import re
import shutil
import subprocess
import unittest
from pathlib import Path


REPO = Path(__file__).resolve().parents[3]


@unittest.skipUnless(shutil.which("kubectl"), "needs kubectl")
class BundleOwnershipTest(unittest.TestCase):
    def test_bundle_leaves_terraform_and_ansible_objects_alone(self):
        rendered = subprocess.run(["kubectl", "kustomize", "openshift"], cwd=REPO,
                                  capture_output=True, text=True, check=True).stdout
        objects = {(re.search(r"^kind: (\S+)$", doc, re.M).group(1), re.search(r"^  name: (\S+)$", doc, re.M).group(1))
                   for doc in rendered.split("\n---\n")}
        self.assertIn(("ConfigMap", "crm-platform"), objects)
        self.assertFalse({kind for kind, _ in objects} & {"NetworkPolicy", "PersistentVolumeClaim"})
        self.assertNotIn(("ConfigMap", "crm-api-config"), objects)


if __name__ == "__main__":
    unittest.main()
