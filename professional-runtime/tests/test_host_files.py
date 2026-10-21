"""Descriptor-only hostile-path tests. No socket, UDS, process or deployment setup."""
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from medical_professional_runtime.host import RuntimeBlocked, digest, file_bytes, profile_relative, write_profile, prepare_job, write_inventory

class HostFileTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / 'pinned'
        self.source.mkdir(mode=0o700)
        self.file = self.source / 'data'
        self.file.write_bytes(b'fixed')
    def tearDown(self): self.temp.cleanup()
    def test_exact_regular_content(self):
        self.assertEqual(b'fixed', file_bytes(self.file, 5, digest(b'fixed')))
    def test_parent_symlink_never_followed(self):
        linked = self.root / 'linked'; linked.symlink_to(self.source, target_is_directory=True)
        with self.assertRaises(OSError): file_bytes(linked/'data', 5)
    def test_final_symlink_never_followed(self):
        linked = self.source / 'linked'; linked.symlink_to(self.file)
        with self.assertRaises(OSError): file_bytes(linked, 5)
    def test_fifo_rejected_without_blocking(self):
        fifo = self.source/'fifo'; os.mkfifo(fifo)
        with self.assertRaises(RuntimeBlocked): file_bytes(fifo, 5)
    def test_rebound_file_rejected(self):
        original = os.fstat; changed = False
        def replacing(fd):
            nonlocal changed
            result = original(fd)
            if result.st_ino == self.file.stat().st_ino and not changed:
                changed = True; self.file.rename(self.source/'old'); self.file.write_bytes(b'fixed')
            return result
        with patch('medical_professional_runtime.host.os.fstat', replacing):
            with self.assertRaises(RuntimeBlocked): file_bytes(self.file, 5)
    def test_relative_and_oversize_rejected(self):
        with self.assertRaises(RuntimeBlocked): file_bytes(Path('relative'), 5)
        with self.assertRaises(RuntimeBlocked): file_bytes(self.file, 4)
    def test_raw_unsafe_components_rejected(self):
        for value in ('profiles','/profiles/x','profiles//x','profiles/./x','profiles/../x','profiles/x/','profiles/a\\b','profiles/%2e','profiles/a\x00','profiles/a\x7f','profiles/a\x85'):
            with self.subTest(value=value), self.assertRaises(RuntimeBlocked): profile_relative(value)
    def test_exact_unicode_and_non_dot_component_preserved(self):
        for value in ('profiles/文档/é.json','profiles/文档/e\u0301.json','profiles/a..b/😺'):
            self.assertEqual(value, str(profile_relative(value)))
    def test_exclusive_private_profile_write(self):
        home = self.root/'home'; home.mkdir(mode=0o700)
        name = 'profiles/文档/e\u0301.json'; write_profile(home, name, b'fixed')
        self.assertEqual(b'fixed', file_bytes(home/name, 5))
        self.assertEqual(0o400, (home/name).stat().st_mode & 0o777)
        with self.assertRaises(FileExistsError): write_profile(home, name, b'changed')
    def test_profile_parent_symlink_rejected(self):
        home = self.root/'home'; home.mkdir(mode=0o700)
        (home/'profiles').symlink_to(self.source, target_is_directory=True)
        with self.assertRaises(OSError): write_profile(home, 'profiles/escape', b'fixed')
        self.assertFalse((self.source/'escape').exists())
    def test_profile_target_symlink_rejected(self):
        home = self.root/'home'; home.mkdir(mode=0o700); (home/'profiles').mkdir(mode=0o700)
        (home/'profiles'/'escape').symlink_to(self.file)
        with self.assertRaises(FileExistsError): write_profile(home, 'profiles/escape', b'changed')
        self.assertEqual(b'fixed', self.file.read_bytes())
    def test_nonprivate_home_rejected(self):
        home = self.root/'home'; home.mkdir(mode=0o755)
        with self.assertRaises(RuntimeBlocked): write_profile(home, 'profiles/data', b'fixed')
    def test_prepared_job_is_exclusive_with_private_children(self):
        jobs = self.root/'jobs'; jobs.mkdir(mode=0o700)
        prepare_job(jobs, ('tenant', 'run', 'step', '1'))
        job = jobs/'tenant'/'run'/'step'/'1'
        for name in ('home', 'workspace', 'tmp'): self.assertEqual(0o700, (job/name).stat().st_mode & 0o777)
        with self.assertRaises(FileExistsError): prepare_job(jobs, ('tenant', 'run', 'step', '1'))
    def test_job_parent_symlink_cannot_create_outside_tree(self):
        jobs = self.root/'jobs'; jobs.mkdir(mode=0o700); (jobs/'tenant').symlink_to(self.source, target_is_directory=True)
        with self.assertRaises(OSError): prepare_job(jobs, ('tenant', 'run', 'step', '1'))
        self.assertFalse((self.source/'run').exists())
    def test_job_parent_rename_during_creation_is_rejected(self):
        jobs = self.root/'jobs'; jobs.mkdir(mode=0o700); original = os.mkdir
        def replacing(path, *args, **kwargs):
            result = original(path, *args, **kwargs)
            if path == '1':
                (jobs/'tenant').rename(jobs/'original'); (jobs/'tenant').symlink_to(self.source, target_is_directory=True)
            return result
        with patch('medical_professional_runtime.host.os.mkdir', replacing):
            with self.assertRaises(RuntimeBlocked): prepare_job(jobs, ('tenant', 'run', 'step', '1'))
        self.assertFalse((self.source/'run').exists())
    def test_inventory_private_exclusive_write(self):
        job = self.root/'job'; job.mkdir(mode=0o700); write_inventory(job, b'fixed')
        self.assertEqual(b'fixed', file_bytes(job/'runtime-inventory.json', 5))
        self.assertEqual(0o400, (job/'runtime-inventory.json').stat().st_mode & 0o777)
        with self.assertRaises(FileExistsError): write_inventory(job, b'changed')
    def test_inventory_parent_rename_never_writes_through_new_symlink(self):
        job = self.root/'job'; job.mkdir(mode=0o700); original = os.open
        def replacing(path, *args, **kwargs):
            result = original(path, *args, **kwargs)
            if path == 'runtime-inventory.json':
                job.rename(self.root/'original-job'); job.symlink_to(self.source, target_is_directory=True)
            return result
        with patch('medical_professional_runtime.host.os.open', replacing):
            with self.assertRaises(RuntimeBlocked): write_inventory(job, b'fixed')
        self.assertFalse((self.source/'runtime-inventory.json').exists())
    def test_read_parent_rename_is_rejected(self):
        original = os.open
        def replacing(path, *args, **kwargs):
            result = original(path, *args, **kwargs)
            if path == 'data':
                self.source.rename(self.root/'original-source'); self.source.symlink_to(self.root, target_is_directory=True)
            return result
        with patch('medical_professional_runtime.host.os.open', replacing):
            with self.assertRaises(RuntimeBlocked): file_bytes(self.file, 5)

if __name__ == '__main__': unittest.main()
