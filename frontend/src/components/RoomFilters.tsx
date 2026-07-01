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
}

export const RoomFilters: React.FC<RoomFiltersProps> =
    ({
         availableBuildings,
         selectedBuildings,
         setSelectedBuildings,
         availableFloors,
         selectedFloors,
         setSelectedFloors }) => {

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
        return (
    <div className="bg-white p-5 rounded-2xl border border-gray-100 mb-6 flex flex-col gap-2">
            {/* building filters */}
        <div className={"flex flex-col gap-2"}>
            <span className="text-sm text-gray-400 mb-1">
                Filter buildings
            </span>

            <div className="flex flex-wrap gap-2">
                {availableBuildings.map((building) => {
                    const isSelected = selectedBuildings.includes(building);

                    return(
                        <button key={building}
                        onClick={() => toggleBuildings(building)}
                        className={`px-4 py-1.5 text-sm rounded-xl transition-all duration-200 border ${ isSelected
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

        {/* floor filters */}
            <span className="text-sm text-gray-400 mb-1">
                Filter floors
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
    );
    };
