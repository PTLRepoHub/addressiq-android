// Conventional Commits — required by release-please, which derives the next
// version and the CHANGELOG entirely from commit messages.
//   feat: …      → minor bump
//   fix: …       → patch bump
//   feat(proto)!: … or a `BREAKING CHANGE:` footer → major bump
// Anything else (chore/docs/ci/refactor/test/build/perf/style/revert) → no release.
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // Warn-only (level 1): an unlisted scope is a nudge, not a blocked PR.
    'scope-enum': [1, 'always', [
      'proto', 'widget', 'ci', 'deps', 'release', 'docs',
      'ui', 'core', 'flow', 'types', 'publish', 'podspec', 'gradle', 'security',
    ]],
    // Long URLs and stack traces belong in bodies.
    'body-max-line-length': [0, 'always'],
  },
};
