// Conventional Commits — required by release-please, which derives the next
// version and the CHANGELOG entirely from commit messages.
//   feat: …      → minor bump
//   fix: …       → patch bump
//   feat(proto)!: … or a `BREAKING CHANGE:` footer → major bump
// Anything else (chore/docs/ci/refactor/test/build/perf/style/revert) → no release.
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'scope-enum': [1, 'always', ['proto', 'ci', 'deps', 'release', 'ui', 'core', 'docs']],
  },
};
