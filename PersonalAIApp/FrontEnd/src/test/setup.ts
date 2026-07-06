import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Mock clipboard API（jsdom 不支持）
Object.defineProperty(navigator, 'clipboard', {
  value: { writeText: vi.fn().mockResolvedValue(undefined) },
  writable: true,
});

// Mock scrollIntoView
Element.prototype.scrollIntoView = vi.fn();

// Mock matchMedia（Ant Design 依赖）
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// Mock getComputedStyle for Ant Design (jsdom does not implement it fully)
const originalGetComputedStyle = window.getComputedStyle;
window.getComputedStyle = (elt: Element, pseudoElt?: string | null) => {
  try {
    return originalGetComputedStyle(elt, pseudoElt);
  } catch {
    return new Proxy({} as CSSStyleDeclaration, {
      get(_, prop) {
        if (prop === 'getPropertyValue') return () => '';
        if (prop === 'getPropertyPriority') return () => '';
        if (prop === 'item') return () => '';
        if (prop === 'length') return 0;
        if (prop === 'cssText') return '';
        if (typeof prop === 'string' && /^\d+$/.test(prop)) return '';
        return '';
      },
    });
  }
};

// Mock window.location.reload
Object.defineProperty(window, 'location', {
  value: { ...window.location, reload: vi.fn() },
  writable: true,
});