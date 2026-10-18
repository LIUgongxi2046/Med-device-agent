"""Real published loader/schema tests. The saved fixture has no production authority."""
import hashlib
import json
from pathlib import Path
import unittest
from xml.etree import ElementTree
from medical_contracts.catalogue import SchemaCatalogue, _merge_published_catalogue
from medical_contracts.errors import SchemaValidationError

ROOT=Path(__file__).resolve().parents[1]
RUNTIME='contracts/extensions/task-runtime.catalogue.json'
FIXTURE='business/medical-persistence/src/test/resources/fixtures/runtime-replay.synthetic.json'

class RuntimeReplayContractsTests(unittest.TestCase):
 def test_complete_fixed_schema_fixture(self):
  catalogue=SchemaCatalogue(ROOT)
  catalogue.bind('replay-manifest',json.loads((ROOT/FIXTURE).read_text()))
  self.assertEqual(503,sum(len(s['$defs']) for s in catalogue.schemas.values()))
 def test_incomplete_or_invented_approval_is_rejected(self):
  catalogue=SchemaCatalogue(ROOT)
  with self.assertRaises(SchemaValidationError):catalogue.bind('replay-manifest',{'schema_version':'1.0.0'})
  fixture=json.loads((ROOT/FIXTURE).read_text());fixture['production_approved']=True
  with self.assertRaises(SchemaValidationError):catalogue.bind('replay-manifest',fixture)
 def test_tampered_runtime_extension_is_rejected(self):
  parts=[(ROOT/p).read_bytes() for p in ['contracts/catalogue.json','contracts/extensions/evaluation-results.catalogue.json','contracts/extensions/simulation-execution.catalogue.json',RUNTIME]]
  parts[-1]+=b'\n'
  with self.assertRaises(ValueError):_merge_published_catalogue(*parts)
 def test_base_remains_frozen(self):
  self.assertEqual('dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915',hashlib.sha256((ROOT/'contracts/catalogue.json').read_bytes()).hexdigest())
 def test_every_runtime_schema_hash_and_packaged_resource(self):
  tree=ElementTree.parse(ROOT/'business/medical-contracts/pom.xml')
  includes={n.text for n in tree.iter() if n.tag.endswith('}include')}
  for e in json.loads((ROOT/RUNTIME).read_text())['schemas']:
   self.assertEqual(e['sha256'],hashlib.sha256((ROOT/e['path']).read_bytes()).hexdigest())
   self.assertIn(e['path'],includes)
  self.assertIn(RUNTIME,includes)
 def test_professional_loader_receives_exact_runtime_extension(self):
  text=(ROOT/'professional-executor/src/catalogue.ts').read_text()
  self.assertEqual(1,text.count("await readRegistered('contracts/extensions/task-runtime.catalogue.json'),"))
  self.assertIn("await readRegistered('contracts/extensions/simulation-execution.catalogue.json'),\n    await readRegistered('contracts/extensions/task-runtime.catalogue.json'),",text)
