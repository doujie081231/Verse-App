import unittest
from architecture_check import boundary_references, graph_errors


class ArchitectureRulesTest(unittest.TestCase):
    def test_comments_and_strings_do_not_create_dependencies(self):
        self.assertEqual(set(), boundary_references('// com.tungsten.Foo\nval url = "org.lwjgl.Foo"'))

    def test_fully_qualified_and_native_calls_are_found(self):
        found = boundary_references('import com.tungsten.fclcore.task.Task\nJvmLauncherAPI.nativeDestroyJVM()')
        self.assertIn('com.tungsten.fclcore.task.Task', found)
        self.assertIn('JNI:JvmLauncherAPI.nativeDestroyJVM', found)

    def test_cycles_and_reverse_dependencies_are_rejected(self):
        self.assertTrue(graph_errors({'VerseApp': {'VerseCore'}, 'VerseCore': {'VerseApp'}}))
        self.assertTrue(graph_errors({'VerseCore': {'FclCore'}}))
        self.assertTrue(graph_errors({'VerseLauncher': {'VerseApp'}}))

    def test_target_direction_is_accepted(self):
        self.assertEqual([], graph_errors({'VerseApp': {'VerseCore', 'VerseLauncher'}, 'VerseLauncher': {'VerseCore', 'FclCore', 'JvmLauncher'}, 'VerseCore': set()}))
