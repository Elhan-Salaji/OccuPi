import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import { Login } from './pages/Login';
import Rooms from './pages/Rooms';
import Analytics from './pages/Analytics';
import { ProtectedRoute } from './components/ProtectedRoute';

function App() {
    return (
        <Router>
            <Routes>
                {/* open routes */}
                <Route path="/login" element={<Login />} />

                {/* startpage redirect to dashboard */}
                <Route path="/" element={<Navigate to="/dashboard" replace />} />

                {/* protected routes */}
                <Route element={<ProtectedRoute />}>
                    <Route path="/dashboard" element={<Dashboard />} />
                    <Route path="/rooms" element={<Rooms />} />
                    <Route path="/analytics" element={<Analytics />} />
                </Route>

                {/* fallback for undefined routes or for tippfehler */}
                <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
        </Router>
    );
}

export default App;