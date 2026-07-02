import React from "react";

interface RoomFiltersProps {
    // buildings
    availableBuildings: string[];
    selectedBuildings: string[];
    setSelectedBuildings: (building: string[]) => void;

    // floors
    availableFloors: string[];
    selectedFloors: string[];
    setSelectedFloors: (floor: string[]) => void;

    // search and dropdown
    search: string;
    setSearch: (value: string) => void;
    statusFilter: string;
    setStatusFilter: (value: string) => void;
    sortBy: string;
    setSortBy: (value: string) => void;
}

export const RoomFilters: React.FC<RoomFiltersProps> =
    ({
         availableBuildings,
         selectedBuildings,
         setSelectedBuildings,
         availableFloors,
         selectedFloors,
         setSelectedFloors,
         search,
         setSearch,
         statusFilter,
         setStatusFilter,
         sortBy,
         setSortBy
     }) => {

        // logic for toggle building checkbox
        const toggleBuildings = (building: string) => {
            //if building is already selected, remove it, else add it
            if (selectedBuildings.includes(building)) {
                setSelectedBuildings(selectedBuildings.filter((b) => b !== building));
            } else {
                // if not selected yet, add it to the list
                setSelectedBuildings([...selectedBuildings, building]);
            }
        };

        const toggleFloor = (floor: string) => {
            //if building is already selected, remove it, else add it
            if (selectedFloors.includes(floor)) {
                setSelectedFloors(selectedFloors.filter((b) => b !== floor));
            } else {
                // if not selected yet, add it to the list
                setSelectedFloors([...selectedFloors, floor]);
            }
        };

        const handleReset = () => {
            setSelectedBuildings([]);
            setSelectedFloors([]);
            setSearch('');
            setStatusFilter('');
            setSortBy('');
        }

        return (
            <div className="bg-white p-5 rounded-2xl border border-gray-100 mb-6 flex flex-col gap-6">
                {/* building filters */}
                <div className={"flex flex-col gap-2"}>
            <span className="text-sm text-gray-400 mb-1">
                Gebäude filtern
            </span>

                    <div className="flex flex-wrap gap-2">
                        {availableBuildings.map((building) => {
                            const isSelected = selectedBuildings.includes(building);

                            return (
                                <button key={building}
                                        onClick={() => toggleBuildings(building)}
                                        className={`px-4 py-1.5 text-sm rounded-xl transition-all duration-200 border ${isSelected
                                            ? 'bg-[#111827] border-[#111827] text-white shadow-sm' // dark blue like the website - test
                                            : 'bg-gray-50 border-gray-200 text-gray-600 hover:bg-gray-100'
                                        }`}
                                >
                                    {building}
                                </button>
                            );
                        })}
                    </div>
                </div>

                {/* the row with filter occupancy and filter floors*/}
                <div className="flex flex-col sm:flex-row gap-6 sm:gap-16">

                    {/* Filter floors */}
                    <div className={"flex flex-col gap-2"}>
                <span className="text-sm text-gray-400 mb-1">
                    Etagen filtern
                </span>

                        <div className="flex flex-wrap gap-2">
                            {availableFloors.map((floor) => {
                                const isSelected = selectedFloors.includes(floor);
                                return (
                                    <button
                                        key={floor}
                                        onClick={() => toggleFloor(floor)}
                                        className={`px-4 py-1.5 text-sm rounded-xl transition-all duration-200 border ${
                                            isSelected
                                                ? 'bg-[#111827] border-[#111827] text-white shadow-sm' // test
                                                : 'bg-gray-50 border-gray-200 text-gray-600 hover:bg-gray-100'
                                        }`}
                                    >
                                        {floor}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    {/* Filter occupancy */}
                    <div className={"flex flex-col gap-2"}>
                <span className="text-sm text-gray-400 mb-1">
                    Auslastung filtern
                </span>

                        <div className="flex flex-wrap gap-2">
                            {/* Low Button*/}
                            <button
                                onClick={() => setStatusFilter(statusFilter === 'low' ? '' : 'low')}
                                className={`px-4 py-1.5 text-sm rounded-xl transition-all duration-200 border ${
                                    statusFilter === 'low'
                                        ? 'bg-green-100 border-green-800 text-green-800'
                                        : 'bg-gray-50 border-gray-200 text-gray-600 hover:bg-gray-100'
                                }`}
                            >
                                Niedrig
                            </button>

                            {/* Medium Button*/}
                            <button
                                onClick={() => setStatusFilter(statusFilter === 'medium' ? '' : 'medium')}
                                className={`px-4 py-1.5 text-sm rounded-xl transition-all duration-200 border ${
                                    statusFilter === 'medium'
                                        ? 'bg-yellow-100 border-yellow-800 text-yellow-800'
                                        : 'bg-gray-50 border-gray-200 text-gray-600 hover:bg-gray-100'
                                }`}
                            >
                                Mittel
                            </button>

                            <button
                                onClick={() => setStatusFilter(statusFilter === 'high' ? '' : 'high')}
                                className={`px-4 py-1.5 text-sm rounded-xl transition-all duration-200 border ${
                                    statusFilter === 'high'
                                        ? 'bg-red-100 border-red-800 text-red-800'
                                        : 'bg-gray-50 border-gray-200 text-gray-600 hover:bg-gray-100'
                                }`}
                            >
                                Hoch
                            </button>
                        </div>
                    </div>

                </div>
                {/* end of the flex row */}

                {/* separation line*/}
                <hr className="border-gray-100"/>

                <div className="flex flex-wrap gap-3">

                    {/* Search input */}
                    <input type="text" placeholder="Suche..." value={search} onChange={(e) => setSearch(e.target.value)}
                           className="bg-white w-full md:w-72 px-4 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-100"/>

                    {/* Status filter
            <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="bg-white px-4 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-100">

                <option value="" disabled hidden>Auslastung</option> // Eher
                <option value="low">Niedrig</option>
                <option value="medium">Mittel</option>
                <option value="high">Hoch</option>
            </select>
            TODO: delete if not needed anymore*/}

                    {/* Sort */}
                    <select
                        value={sortBy}
                        onChange={(e) => setSortBy(e.target.value)}
                        className="bg-white px-4 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-100"
                    >
                        <option value="" disabled>Sortierung</option>
                        <option value="least">Auslastung: Niedrigste zuerst</option>
                        <option value="most">Auslastung: Höchste zuerst</option>
                        <option value="building">Gebäude: Alphabetisch</option>
                    </select>

                    {/* reset button */}
                    <button
                        onClick={handleReset}
                        className="ml-auto px-4 py-2 text-sm text-red-500 hover:bg-red-100 rounded-xl transition-colors font-medium border border-red-200"
                    >
                        Filter löschen
                    </button>

                </div>
            </div>
        );
    };