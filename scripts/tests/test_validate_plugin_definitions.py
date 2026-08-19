"""Unit tests for validate-plugin-definitions.py in devcontainer."""

from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VALIDATOR_PATH = ROOT / "scripts" / "validate-plugin-definitions.py"

spec = importlib.util.spec_from_file_location("validate_plugin_definitions", VALIDATOR_PATH)
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)


class ValidatePluginDefinitionsUnitTests(unittest.TestCase):
    def test_strip_markdown_code_blocks_removes_fenced_and_inline_code(self) -> None:
        text = (
            "Intro text\n"
            "```markdown\n[fenced](broken/link.md)\n```\n"
            "Middle text with `[inline](broken/link2.md)` code\n"
            "Active link: [active](valid.md)"
        )
        stripped = v.strip_markdown_code_blocks(text)
        self.assertNotIn("broken/link.md", stripped)
        self.assertNotIn("broken/link2.md", stripped)
        self.assertIn("[active](valid.md)", stripped)

    def test_validate_markdown_links_accepts_valid_relative_links(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            target = tmp_path / "target.md"
            target.write_text("# Target\n", encoding="utf-8")
            source = tmp_path / "source.md"
            source.write_text("See [target](target.md#section) for details.\n", encoding="utf-8")

            v.validate_markdown_links(source)

    def test_validate_markdown_links_rejects_broken_relative_links(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            source = tmp_path / "source.md"
            source.write_text("See [broken](nonexistent.md) for details.\n", encoding="utf-8")

            with self.assertRaises(v.ValidationError) as ctx:
                v.validate_markdown_links(source)
            self.assertIn("contains broken relative link target", str(ctx.exception))

    def test_validate_markdown_links_rejects_absolute_file_links(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            source = tmp_path / "source.md"
            source.write_text("See [abs](file:///tmp/something.md) for details.\n", encoding="utf-8")

            with self.assertRaises(v.ValidationError) as ctx:
                v.validate_markdown_links(source)
            self.assertIn("must not use absolute file URL", str(ctx.exception))

    def test_parse_skill_frontmatter_handles_valid_and_invalid_yaml(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            skill_file = tmp_path / "SKILL.md"
            skill_file.write_text("---\nname: my-skill\ndescription: Use when testing.\n---\n# Content\n", encoding="utf-8")
            fm = v.parse_skill_frontmatter(skill_file)
            self.assertEqual(fm.get("name"), "my-skill")
            self.assertEqual(fm.get("description"), "Use when testing.")

            no_fm = tmp_path / "NO_FM.md"
            no_fm.write_text("# Content without frontmatter\n", encoding="utf-8")
            with self.assertRaises(v.ValidationError) as ctx:
                v.parse_skill_frontmatter(no_fm)
            self.assertIn("must start with YAML frontmatter", str(ctx.exception))

            unclosed_fm = tmp_path / "UNCLOSED.md"
            unclosed_fm.write_text("---\nname: unclosed\n", encoding="utf-8")
            with self.assertRaises(v.ValidationError) as ctx:
                v.parse_skill_frontmatter(unclosed_fm)
            self.assertIn("must close YAML frontmatter", str(ctx.exception))

    def test_validate_skill_spec_enforces_use_when_and_length_limits(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            skill_dir = tmp_path / "sample-skill"
            skill_dir.mkdir()
            skill_file = skill_dir / "SKILL.md"

            # Rejects non-"Use when..."
            skill_file.write_text("---\nname: sample-skill\ndescription: Do something for testing.\nallowed-tools: Read\n---\n", encoding="utf-8")
            with self.assertRaises(v.ValidationError) as ctx:
                v.validate_skill_spec(skill_dir)
            self.assertIn("description must begin with 'Use when...'", str(ctx.exception))

            # Rejects description > 1024 chars
            long_desc = "Use when " + ("x" * 1020)
            skill_file.write_text(f"---\nname: sample-skill\ndescription: {long_desc}\nallowed-tools: Read\n---\n", encoding="utf-8")
            with self.assertRaises(v.ValidationError) as ctx:
                v.validate_skill_spec(skill_dir)
            self.assertIn("description exceeds 1024 characters", str(ctx.exception))

    def test_validate_skill_spec_accepts_user_invoked_custom_description(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            skill_dir = tmp_path / "user-invoked"
            skill_dir.mkdir()
            skill_file = skill_dir / "SKILL.md"
            skill_file.write_text("---\nname: user-invoked\ndescription: A user-invoked action.\ndisable-model-invocation: true\nallowed-tools: Read Edit\n---\n# Action\n", encoding="utf-8")
            v.validate_skill_spec(skill_dir)

    def test_validate_agents_and_rules(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            agents_dir = tmp_path / "agents"
            agents_dir.mkdir()
            agent_file = agents_dir / "custom-agent.md"
            agent_file.write_text(
                "# Custom Agent\n\n"
                "- **Skills**: `tdd`, `vcs`, `unknown-skill`\n",
                encoding="utf-8",
            )
            with self.assertRaises(v.ValidationError) as ctx:
                v.validate_agents_and_rules(tmp_path, {"tdd", "vcs"})
            self.assertIn("references unknown skill 'unknown-skill'", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
