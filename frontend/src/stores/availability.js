import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';

export const useAvailabilityStore = defineStore('availability', () => {
    const userAvailabilities = ref(new Map());
    const roomAvailabilities = ref(new Map());
    const changedUserAvailabilities = ref(new Set());
    const changedRoomAvailabilities = ref(new Set());

    async function fetchAvailabilities(eventId) {
        const [userRes, roomRes] = await Promise.all([
            api.get(`/api/admin/veranstaltungen/${eventId}/verfuegbarkeiten`),
            api.get(`/api/admin/veranstaltungen/${eventId}/raeume/verfuegbarkeiten`)
        ]);

        userAvailabilities.value.clear();
        userRes.data.forEach(dto => {
            userAvailabilities.value.set(dto.nutzerId, new Set(dto.verfuegbareSlotIds));
        });

        roomAvailabilities.value.clear();
        roomRes.data.forEach(dto => {
            roomAvailabilities.value.set(dto.raumId, new Set(dto.verfuegbareSlotIds));
        });

        changedUserAvailabilities.value.clear();
        changedRoomAvailabilities.value.clear();
    }

    function toggleUserAvailability(userId, slotId) {
        const userSlots = userAvailabilities.value.get(userId);
        if (userSlots) {
            if (userSlots.has(slotId)) {
                userSlots.delete(slotId);
            } else {
                userSlots.add(slotId);
            }
            changedUserAvailabilities.value.add(userId);
        }
    }

    function toggleRoomAvailability(roomId, slotId) {
        const roomSlots = roomAvailabilities.value.get(roomId);
        if (roomSlots) {
            if (roomSlots.has(slotId)) {
                roomSlots.delete(slotId);
            } else {
                roomSlots.add(slotId);
            }
            changedRoomAvailabilities.value.add(roomId);
        }
    }

    async function saveAvailabilities(eventId) {
        const userPromises = [];
        for (const userId of changedUserAvailabilities.value) {
            const payload = {
                nutzerId: userId,
                veranstaltungId: eventId,
                verfuegbareSlotIds: Array.from(userAvailabilities.value.get(userId) || [])
            };
            userPromises.push(api.post(`/api/admin/veranstaltungen/${eventId}/verfuegbarkeiten`, payload));
        }

        const roomPromises = [];
        for (const roomId of changedRoomAvailabilities.value) {
            const payload = {
                raumId: roomId,
                veranstaltungId: eventId,
                verfuegbareSlotIds: Array.from(roomAvailabilities.value.get(roomId) || [])
            };
            roomPromises.push(api.post(`/api/admin/veranstaltungen/${eventId}/raeume/verfuegbarkeiten`, payload));
        }

        await Promise.all([...userPromises, ...roomPromises]);
        changedUserAvailabilities.value.clear();
        changedRoomAvailabilities.value.clear();
    }

    const isUserAvailable = (userId, slotId) => {
        return userAvailabilities.value.get(userId)?.has(slotId) ?? true;
    };

    const isRoomAvailable = (roomId, slotId) => {
        return roomAvailabilities.value.get(roomId)?.has(slotId) ?? true;
    };

    const hasDirtyAvailabilities = () => {
        return changedUserAvailabilities.value.size > 0 || changedRoomAvailabilities.value.size > 0;
    };

    return {
        userAvailabilities,
        roomAvailabilities,
        fetchAvailabilities,
        toggleUserAvailability,
        toggleRoomAvailability,
        saveAvailabilities,
        isUserAvailable,
        isRoomAvailable,
        hasDirtyAvailabilities
    };
});