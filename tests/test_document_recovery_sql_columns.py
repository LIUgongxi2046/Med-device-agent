"""Repository-schema regression for technical-check recovery SQL; no database required."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re,unittest,os
import sqlglot
from sqlglot.optimizer.qualify import qualify
from test_evaluation_policy_columns import catalogue, ROOT
class TechnicalCheckRecoverySqlColumns(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.schema={table:{col:'UNKNOWN' for col in cols} for table,cols in catalogue().items()}
  path=Path(os.environ.get('DOCUMENT_MAPPER',str(ROOT/'business/medical-persistence/src/main/resources/medical-mappers/DocumentTechnicalCheckMapper.xml')))
  cls.sql=''.join(ET.parse(path).getroot().find("select[@id='recoveryStartAudit']").itertext())
 def test_real_table_and_columns_exist(self):
  qualify(sqlglot.parse_one(re.sub(r'#\{[^}]+\}',"'value'",self.sql),read='mysql'),dialect='mysql',schema=self.schema,validate_qualify_columns=True)
 def test_original_actor_and_operation_are_bound(self):
  for fragment in ('FROM audit_event','request_id=#{operationId}','trace_id=#{operationId}','subject_id=#{creatorId}','created_by=#{creatorId}',"result='SUCCESS'",'reason_code IS NULL'):
   self.assertIn(fragment,self.sql)
  self.assertNotIn('audit_log',self.sql)
  self.assertNotIn('AND operation_id=',self.sql)
 def test_old_nonexistent_table_is_demonstrably_rejected(self):
  self.assertNotIn('audit_log',self.schema)
  self.assertNotIn('operation_id',self.schema['audit_event'])
  self.assertIn('request_id',self.schema['audit_event']);self.assertIn('trace_id',self.schema['audit_event'])
if __name__=='__main__':unittest.main(verbosity=2)
