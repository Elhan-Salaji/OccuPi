import { Search } from 'lucide-react';
import { User } from 'lucide-react';

export const Navbar = () => {
    return (
        <header className="h-20 bg-white border-b border-gray-200 flex items-center justify-between px-6 shadow-sm z-10 shrink-0">

            {/* Search Bar */}
            <div className="flex-1 max-w-md">
                <div className="relative">
                    {/* Magnifying Glass Icon */}
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                        <Search className="h-5 w-5 text-gray-400" />
                    </div>

                    {/* The actual search bar*/}
                    <input
                        type="text"
                        placeholder="Search rooms or buildings..."
                        className="block w-full pl-10 pr-3 py-2 border border-gray-200 rounded-lg bg-gray-50 text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:bg-white focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-colors"
                    />
                </div>
            </div>


            {/* User Status */}
            <div className="ml-6 flex items-center space-x-3 bg-gray-50 py-2 px-4 rounded-lg border border-gray-100">
                <div className="bg-white p-1.5 rounded-md border border-gray-200 shadow-sm">
                    <User className="h-5 w-5 text-gray-600" />
                </div>
                <div className="text-sm">
                    <p className="text-gray-900">
                        Logged in as: <span className="font-bold">[Student Name]</span>
                        {/* TODO: delete mock name and mock role and fetch + show Username and Role from Keycloak token*/}
                    </p>
                    <p className="text-gray-500 text-xs mt-0.5">
                        Role: <span className="font-medium text-gray-500">Student</span>
                    </p>
                </div>
            </div>

        </header>
    );
};