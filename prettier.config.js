/** @type {import('prettier').Config} */
export default {
  arrowParens: 'always',
  bracketSpacing: true,
  endOfLine: 'auto',
  htmlWhitespaceSensitivity: 'css',
  insertPragma: false,
  bracketSameLine: false,
  singleQuote: true,
  jsxSingleQuote: false,
  printWidth: 80,
  proseWrap: 'always',
  quoteProps: 'as-needed',
  requirePragma: false,
  semi: false,
  tabWidth: 2,
  trailingComma: 'all',
  useTabs: false,
  overrides: [
    {
      files: ['**/*.md', '**/*.mdx'],
      options: {
        proseWrap: 'preserve',
      },
    },
  ],
}
