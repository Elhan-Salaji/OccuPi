import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '../hooks/useAuthStore';

interface ProtectedRouteProps {
    role?: string;
}

export const ProtectedRoute = ({ role }: ProtectedRouteProps) => {
    const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
    const user = useAuthStore((state) => state.user);

    // If not logged in, redirect to login page
    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    if (role && user?.role !== role){
        return <Navigate to="/dashboard" replace />;
    }

    // if logged in, render the protected route
    return <Outlet />;
};