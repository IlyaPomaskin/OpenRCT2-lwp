/*****************************************************************************
 * Copyright (c) 2014-2021 OpenRCT2 developers
 *
 * For a complete list of all authors, please refer to contributors.md
 * Interested in contributing? Visit https://github.com/OpenRCT2/OpenRCT2
 *
 * OpenRCT2 is licensed under the GNU General Public License version 3.
 *****************************************************************************/

#pragma once

#include "../rct2/RCT2.h"
#include "RideColour.h"
#include "ShopItem.h"
#include "VehicleEntry.h"

#include <cstdint>

// TODO: MOVE TO COMMON LIMITS HEADER DUPLICATED FROM RIDE.H
constexpr const uint8_t NUM_SHOP_ITEMS_PER_RIDE_2 = 2;

// The max number of different types of vehicle.
// Examples of vehicles here are the locomotive, tender and carriage of the Miniature Railway.
#define MAX_VEHICLES_PER_RIDE_ENTRY 4

struct RideNaming
{
    rct_string_id Name;
    rct_string_id Description;
};

struct track_colour_preset_list
{
    uint8_t count;
    TrackColour list[256];
};

struct vehicle_colour_preset_list
{
    uint8_t count;
    vehicle_colour list[256];
};

/**
 * Ride type structure.
 */
struct rct_ride_entry
{
    RideNaming naming;
    // The first three images are previews. They correspond to the ride_type[] array.
    uint32_t images_offset;
    uint32_t flags;
    uint8_t ride_type[RCT2_MAX_RIDE_TYPES_PER_RIDE_ENTRY];
    uint8_t min_cars_in_train;
    uint8_t max_cars_in_train;
    uint8_t cars_per_flat_ride;
    // Number of cars that can't hold passengers
    uint8_t zero_cars;
    // The index to the vehicle type displayed in the vehicle tab.
    uint8_t tab_vehicle;
    uint8_t default_vehicle;
    // Convert from first - fourth vehicle to vehicle structure
    uint8_t front_vehicle;
    uint8_t second_vehicle;
    uint8_t rear_vehicle;
    uint8_t third_vehicle;
    uint8_t BuildMenuPriority;
    rct_ride_entry_vehicle vehicles[RCT2_MAX_VEHICLES_PER_RIDE_ENTRY];
    vehicle_colour_preset_list* vehicle_preset_list;
    int8_t excitement_multiplier;
    int8_t intensity_multiplier;
    int8_t nausea_multiplier;
    uint8_t max_height;
    ShopItem shop_item[NUM_SHOP_ITEMS_PER_RIDE_2];
    rct_string_id capacity;
    void* obj;

    const rct_ride_entry_vehicle* GetVehicle(size_t id) const
    {
        if (id < std::size(vehicles))
        {
            return &vehicles[id];
        }
        return nullptr;
    }

    const rct_ride_entry_vehicle* GetDefaultVehicle() const
    {
        return GetVehicle(default_vehicle);
    }
};

void set_vehicle_type_image_max_sizes(rct_ride_entry_vehicle* vehicle_type, int32_t num_images);
RideNaming get_ride_naming(const uint8_t rideType, rct_ride_entry* rideEntry);
