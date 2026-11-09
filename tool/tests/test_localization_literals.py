from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from localization_literals import failures


class FailureTextTests(unittest.TestCase):
    def test_native_failures_preserve_interpolation(self):
        source = 'require(ok) { "Failed for ${item.name}" }; throw IOException("Closed connection")'
        values = [(value, args) for _, _, value, args in failures(source, True)]
        self.assertEqual(values, [('Failed for {0}', ['item.name']), ('Closed connection', [])])

    def test_protocol_error_codes_and_json_fields_are_not_display_text(self):
        source = 'result.error("permission_denied", text, null); check(ok) { data.optString("error", data.optString("output")) }'
        self.assertEqual(list(failures(source, True)), [])

    def test_dart_background_errors_are_detected(self):
        source = "throw const HttpException('Server returned ${response.statusCode}')"
        values = [(value, args) for _, _, value, args in failures(source, False)]
        self.assertEqual(values, [('Server returned {0}', ['response.statusCode'])])

    def test_embedded_scripts_and_comments_are_not_rewritten(self):
        source = '// throw IOException("Comment")\nval script = """check(ok) { "Guest log" }"""'
        self.assertEqual(list(failures(source, True)), [])


if __name__ == '__main__':
    unittest.main()
