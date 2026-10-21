"""Guard the narrow generated-static cleanup lifecycle and ownership boundary."""
from pathlib import Path
import unittest
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


class FrontendBuildCleanupTest(unittest.TestCase):
    def test_cleanup_is_module_local_before_resource_copy_and_narrow(self):
        pom = ET.parse(ROOT / 'business/ruoyi-admin/pom.xml')
        plugins = [p for p in pom.findall('./m:build/m:plugins/m:plugin', NS)
                   if p.findtext('m:artifactId', namespaces=NS) == 'maven-clean-plugin']
        self.assertEqual(1, len(plugins))
        plugin = plugins[0]
        self.assertEqual('3.4.1', plugin.findtext('m:version', namespaces=NS))
        executions = plugin.findall('m:executions/m:execution', NS)
        self.assertEqual(1, len(executions))
        execution = executions[0]
        self.assertEqual('generate-resources', execution.findtext('m:phase', namespaces=NS))
        self.assertEqual(['clean'], [e.text for e in execution.findall('m:goals/m:goal', NS)])
        config = execution.find('m:configuration', NS)
        self.assertEqual('true', config.findtext('m:excludeDefaultDirectories', namespaces=NS))
        self.assertEqual('true', config.findtext('m:failOnError', namespaces=NS))
        filesets = config.findall('m:filesets/m:fileset', NS)
        self.assertEqual(1, len(filesets))
        self.assertEqual('${project.build.outputDirectory}/static', filesets[0].findtext('m:directory', namespaces=NS))
        self.assertEqual('false', filesets[0].findtext('m:followSymlinks', namespaces=NS))
        self.assertIsNone(plugin.find('m:configuration', NS), 'Do not change explicit clean lifecycle semantics')
        self.assertFalse((ROOT / 'business/ruoyi-admin/src/main/resources/static').exists())
        for pom_path in (ROOT / 'business').glob('**/pom.xml'):
            if pom_path != ROOT / 'business/ruoyi-admin/pom.xml':
                self.assertNotIn('refresh-generated-frontend', pom_path.read_text())


if __name__ == '__main__':
    unittest.main()
