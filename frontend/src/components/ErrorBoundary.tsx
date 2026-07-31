import { Component } from 'react';
import type { ReactNode } from 'react';

interface ErrorBoundaryProps {
    children: ReactNode;
}

interface ErrorBoundaryState {
    hasError: boolean;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
    state: ErrorBoundaryState = { hasError: false};

    static getDerivedStateFromError() {
        return { hasError: true};
    }

    render() {
        if (this.state.hasError) {
            return (
                <div className="bg-gray-50 rounded-lg p-6 text-center text-gray-400">
                    Diese Ansicht konnte nicht geladen werden.
                </div>
            );
        }
        return this.props.children;
    }
}