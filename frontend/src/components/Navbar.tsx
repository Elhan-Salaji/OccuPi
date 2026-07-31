import { Menu, User } from 'lucide-react';
import { useAuthStore} from "../hooks/useAuthStore";

export const Navbar = ({ onMenuClick }: { onMenuClick: () => void }) => {
    const { user } = useAuthStore();

    return (
        <header className="h-20 bg-white border-b border-gray-200 flex items-center px-4 md:px-6 shadow-sm z-10 shrink-0">
            {/* Hamburger — only Mobile */}
            <button onClick={onMenuClick} aria-label="Menü öffnen"
                    className="md:hidden p-2 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors">
                <Menu className="h-6 w-6" />
            </button>

            {/* User Status */}
            <div className="ml-auto flex items-center space-x-3 bg-gray-50 py-2 px-4 rounded-lg border border-gray-100">
                <div className="bg-white p-1.5 rounded-md border border-gray-200 shadow-sm">
                    <User className="h-5 w-5 text-gray-600" />
                </div>
                <div className="text-sm">
                    <p className="text-gray-900">
                        Logged in as: <span className="font-bold">{user?.username ?? 'Unbenannt'}</span>
                    </p>
                    <p className="text-gray-500 text-xs mt-0.5">
                        Role: <span className="font-medium text-gray-500">{user?.role ?? 'Unbekannt'}</span>
                    </p>
                </div>
            </div>

        </header>
    );
};