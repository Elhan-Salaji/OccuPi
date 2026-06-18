import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import { Login } from './pages/Login';
import Rooms from './pages/Rooms';
import Analytics from './pages/Analytics';
import { ProtectedRoute } from './components/ProtectedRoute';
import { MainLayout } from './layouts/MainLayout.tsx';

function App() {
    // Auth state is restored synchronously when the store is created
    // (see useAuthStore → loadInitialAuth), so no init effect is needed here.
    return (
        <Router>
            <Routes>
                {/* open routes */}
                <Route path="/login" element={<Login />} />

                {/* startpage redirect to dashboard */}
                <Route path="/" element={<Navigate to="/dashboard" replace />} />

                {/* protected routes */}
                <Route element={<ProtectedRoute />}>
                    <Route element={<MainLayout />}>
                        <Route path="/dashboard" element={<Dashboard />} />
                        <Route path="/rooms" element={<Rooms />} />
                        <Route path="/analytics" element={<Analytics />} />
                    </Route>
                </Route>

                <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
        </Router>
    );
}

export default App;
