import type { StorybookConfig } from '@storybook/react-vite';

const config: StorybookConfig = {
  stories: ['../DS/**/*.stories.@(js|jsx|mjs|ts|tsx)'],
  addons: [],
  framework: {
    name: '@storybook/react-vite',
    options: {}
  },
  docs: {
    autodocs: 'tag'
  }
};

export default config;
