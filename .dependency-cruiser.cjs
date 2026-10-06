/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      severity: 'error',
      comment: 'Production modules must not form circular dependency chains.',
      from: {},
      to: { circular: true }
    },
    {
      name: 'no-orphans',
      severity: 'error',
      comment: 'Source modules must be reachable from the public API or a supported entry point.',
      from: {
        orphan: true,
        pathNot: [
          '^DS/index\\.ts$',
          '^DS/styles\\.d\\.ts$',
          '[.](?:stories|test)[.]tsx?$',
          '^DS/test/'
        ]
      },
      to: {}
    },
    {
      name: 'not-to-test',
      severity: 'error',
      comment: 'Production modules must not import test or Storybook files.',
      from: { pathNot: '[.](?:stories|test)[.]tsx?$' },
      to: { path: '[.](?:stories|test)[.]tsx?$' }
    },
    {
      name: 'not-to-unresolvable',
      severity: 'error',
      comment: 'Every imported module must resolve from the project configuration.',
      from: {},
      to: { couldNotResolve: true }
    },
    {
      name: 'no-non-package-json',
      severity: 'error',
      comment: 'External runtime modules must be declared in package.json.',
      from: {},
      to: { dependencyTypes: ['npm-no-pkg', 'npm-unknown'] }
    }
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    exclude: { path: '^DS/(?:dist/|vitest\\.config\\.ts$)' },
    includeOnly: ['^DS'],
    // TypeScript 7's compiler API is not supported by dependency-cruiser yet.
    // SWC parses TypeScript independently, while `npm run typecheck` validates types.
    parser: 'swc',
    enhancedResolveOptions: {
      extensions: ['.ts', '.tsx', '.js', '.jsx'],
      mainFields: ['module', 'main', 'types', 'typings']
    }
  }
};
