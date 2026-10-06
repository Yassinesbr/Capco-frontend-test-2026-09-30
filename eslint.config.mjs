import js from '@eslint/js'
import { fixupPluginRules } from '@eslint/compat'
import { defineConfig, globalIgnores } from 'eslint/config'
import { importX } from 'eslint-plugin-import-x'
import eslintPluginPrettierRecommended from 'eslint-plugin-prettier/recommended'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig([
  globalIgnores([
    '.next/**',
    '.storybook/**',
    'coverage/**',
    'dist/**',
    'DS/dist/**',
    'frontend/.react-router/**',
    'frontend/build/**',
    'next-env.d.ts',
    'node_modules/**',
    'playwright-report/**',
    'storybook-static/**',
    'test-results/**',
  ]),
  {
    files: ['**/*.{js,mjs,cjs,jsx,ts,tsx}'],
    plugins: {
      'import-x': importX,
      react: fixupPluginRules(react),
      'react-hooks': reactHooks,
    },
    settings: {
      react: {
        version: 'detect',
      },
    },
    rules: {
      ...js.configs.recommended.rules,
      ...react.configs.recommended.rules,
      ...reactHooks.configs['recommended-latest'].rules,
      'import-x/order': [
        'error',
        {
          alphabetize: {
            order: 'asc',
            caseInsensitive: true,
          },
          groups: [
            'builtin',
            'external',
            'internal',
            'unknown',
            'parent',
            'sibling',
            'index',
            'object',
            'type',
          ],
          'newlines-between': 'always',
          pathGroupsExcludedImportTypes: ['builtin', 'object'],
        },
      ],
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
      curly: ['error', 'all'],
      'default-case': 'error',
      eqeqeq: ['error', 'always'],
      'no-alert': 'error',
      'no-console': ['error', { allow: ['warn', 'error'] }],
      'no-implicit-coercion': 'error',
      'no-param-reassign': 'error',
      'no-warning-comments': [
        'error',
        { terms: ['todo', 'fixme', 'xxx'], location: 'anywhere' },
      ],
      'react/jsx-no-leaked-render': 'error',
      'react/no-array-index-key': 'error',
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'react',
              importNames: ['FormEvent', 'SyntheticEvent'],
              message:
                'Use React 19 form actions or inferred DOM event types; do not introduce legacy synthetic event types.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parser: tseslint.parser,
      globals: globals.browser,
      parserOptions: {
        projectService: {
          allowDefaultProject: [
            'frontend/e2e/*.ts',
            'frontend/playwright.config.ts',
            'frontend/vitest.config.ts',
          ],
        },
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      '@typescript-eslint': tseslint.plugin,
    },
    rules: {
      ...tseslint.configs.recommendedTypeChecked.rules,
      'no-undef': 'off',
      'no-unused-vars': 'off',
      'consistent-return': 'error',
      curly: ['error', 'all'],
      eqeqeq: 'error',
      'no-console': 'error',
      'no-duplicate-imports': 'error',
      'no-nested-ternary': 'error',
      '@typescript-eslint/no-unused-vars': 'error',
      'object-shorthand': ['error', 'always'],
      'prefer-const': 'error',
      'prefer-template': 'error',
    },
  },
  {
    files: ['frontend/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'react',
              importNames: ['FormEvent', 'SyntheticEvent'],
              message:
                'Use React 19 form actions or inferred DOM event types; do not introduce legacy synthetic event types.',
            },
          ],
          patterns: [
            {
              group: ['@mui/*', '@emotion/*'],
              message:
                'Application UI must use React Aria and public components from @kyc/ds.',
            },
            {
              group: ['../src/*', '../../src/*', '../../../src/*'],
              message:
                'Application UI must import the public @kyc/ds API, not library internals.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['frontend/**/*.mjs'],
    languageOptions: {
      globals: globals.node,
    },
  },
  eslintPluginPrettierRecommended,
])
