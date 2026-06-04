import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../hooks/useAuthStore';

export const Login = () => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');

    const login = useAuthStore((state) => state.login);
    const navigate = useNavigate();

    // Task: Form validation & Submit handling
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        // simple validation
        if (!username.trim() || !password.trim()) {
            setError('Bitte fülle alle Felder aus.');
            return;
        }

        const success = await login(username, password);

        if (success) {
            // Login successful -> redirect to dashboard
            navigate('/');
        } else {
            setError('Ungültiger Username oder Passwort. (Nutze admin / admin123)'); //Test Daten
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-[#F3F4F6] px-4 font-sans text-[#1F2937]">

            {/* login card with rounded corners and shadow test*/}
            <div className="w-full max-w-md bg-white rounded-xl shadow-xl p-8 md:p-10 transition-all">

                {/* logo / branding header*/}
                <div className="text-center mb-8">
                    <h1 className="text-3xl font-bold tracking-tigh text-[#111827]">OccuPi</h1>
                    <p className="text-sm text-[#6B7280] mt-2">Room Occupancy System</p>
                </div>

                {/* validation error message */}
                {error && (
                    <div className="mb-4 p-3 bg-red-25 text-red-600 text-sm rounded-xl border border-red-100">
                        {error}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="space-y-5">
                    {/* Username input */}
                    <div>
                        <label className="block text-xs font-semibold text-[#4B5563] uppercase tracking-wider mb-2 ml-1">
                            Benutzername/Hochschulkürzel
                        </label>
                        <input
                            type="text"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            placeholder="z.B. td057"
                            className="w-full px-4 py-3.5 bg-[#F9FAFB] border border-[#E5E7EB] rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#111827] focus:bg-white transition-all"
                        />
                    </div>

                    {/* Password input */}
                    <div>
                        <label className="block text-xs font-semibold text-[#4B5563] uppercase tracking-wider mb-2 ml-1">
                            Passwort
                        </label>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="Passwort"
                            className="w-full px-4 py-3.5 bg-[#F9FAFB] border border-[#E5E7EB] rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#111827] focus:bg-white transition-all"
                        />
                    </div>

                    {/* Remember me & Info */}
                    <div className="flex items-center justify-between text-xs text-[#6B7280] px-1">
                        <label className="flex items-center space-x-2 cursor-pointer">
                            <input type="checkbox" className="rounded border-gray-300 text-[#111827] focus:ring-[#111827]" />
                            <span>Angemeldet bleiben</span>
                        </label>
                    </div>

                    {/* Button */}
                    <button
                        type="submit"
                        className="w-full mt-2 py-3.5 px-4 bg-[#111827] hover:bg-[#1F2937] text-white font-medium rounded-xl text-sm shadow-sm hover:shadow transition-all duration-200 active:scale-[0.98]"
                    >
                        Anmelden
                    </button>
                </form>

                {/* footer*/}
                <div className="mt-8 text-center border-t border-[#F3F4F6] pt-6">
                    //you can add a footer message here
                </div>

            </div>
        </div>
    );
};