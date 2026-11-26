import copy
import unittest
from prototypes.server import validate_action, validate_request

class BackendTests(unittest.TestCase):
    def setUp(self):
        self.snapshot = {"width": 400, "height": 800, "sensitive": False, "nodes": [
            {"id": "1", "label": "Pay ₹1450", "disabled": False, "editable": False,
             "x": 0, "y": 100, "width": 100, "height": 50}]}
        self.action = {"action": "CLICK", "target_id": "1", "requires_confirmation": False,
                       "action_summary": "pay ₹1450", "spoken_feedback": "Found payment"}
    def test_forces_confirmation(self):
        self.assertTrue(validate_action(self.action, self.snapshot)["requires_confirmation"])
    def test_rejects_missing_disabled_and_editable_targets(self):
        for change in ({"id": "2"}, {"disabled": True}, {"editable": True}):
            snapshot = copy.deepcopy(self.snapshot)
            snapshot["nodes"][0].update(change)
            with self.assertRaises(ValueError): validate_action(self.action, snapshot)
    def test_rejects_string_boolean(self):
        self.action["requires_confirmation"] = "false"
        with self.assertRaises(ValueError): validate_action(self.action, self.snapshot)
    def test_drops_input_values_and_url(self):
        self.snapshot["nodes"][0]["value"] = "secret"
        self.snapshot["url"] = "https://site/?private=secret"
        parts = validate_request({"command": "help", "snapshot": self.snapshot})
        self.assertNotIn("secret", str(parts))
    def test_sensitive_screenshot_rejected(self):
        self.snapshot["sensitive"] = True
        with self.assertRaises(ValueError): validate_request({"command":"help", "snapshot":self.snapshot, "image":"/9j/"})
    def test_duplicate_ids_and_nonfinite_bounds_rejected(self):
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["nodes"].append(copy.deepcopy(snapshot["nodes"][0]))
        with self.assertRaises(ValueError): validate_request({"command":"help", "snapshot":snapshot})
        self.snapshot["nodes"][0]["x"] = float("nan")
        with self.assertRaises(ValueError): validate_request({"command":"help", "snapshot":self.snapshot})
    def test_unsupported_action_rejected(self):
        self.action["action"] = "EXECUTE_SCRIPT"
        with self.assertRaises(ValueError): validate_action(self.action, self.snapshot)

if __name__ == "__main__": unittest.main()
