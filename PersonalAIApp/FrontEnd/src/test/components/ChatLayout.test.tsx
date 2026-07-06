import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import ChatLayout from '../../components/Layout/ChatLayout';

const { mockStore } = vi.hoisted(() => {
  const mockStore = {
    loadModels: vi.fn(),
    loadSessions: vi.fn(),
  };
  return { mockStore };
});

vi.mock('../../stores/appStore', () => {
  const fn = (selector?: any) => {
    if (typeof selector === 'function') return selector(mockStore);
    return mockStore;
  };
  fn.getState = () => mockStore;
  fn.setState = (partial: any) => Object.assign(mockStore, partial);
  return { useAppStore: fn };
});

// Mock SessionList
vi.mock('../../components/SessionList/SessionList', () => ({
  default: () => <div data-testid="session-list">SessionList</div>,
}));

// Mock ChatArea
vi.mock('../../components/ChatArea', () => ({
  default: () => <div data-testid="chat-area">ChatArea</div>,
}));

describe('ChatLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render SessionList and ChatArea', () => {
    render(<ChatLayout />);
    expect(document.querySelector('[data-testid="session-list"]')).toBeInTheDocument();
    expect(document.querySelector('[data-testid="chat-area"]')).toBeInTheDocument();
  });

  it('should call loadModels on mount', () => {
    render(<ChatLayout />);
    expect(mockStore.loadModels).toHaveBeenCalled();
  });

  it('should call loadSessions on mount', () => {
    render(<ChatLayout />);
    expect(mockStore.loadSessions).toHaveBeenCalled();
  });

  it('should render layout with 100vh height', () => {
    render(<ChatLayout />);
    const layout = document.querySelector('.ant-layout');
    expect(layout).toBeInTheDocument();
    expect(layout?.getAttribute('style')).toContain('height: 100vh');
  });
});