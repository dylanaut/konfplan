import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';

export const useAvailabilityStore = defineStore('availability', () => {
    const userAvailabilities = ref(new Map());
    const roomAvailabilities = ref(new Map());
    const roomBlockingInfo = ref(new Map()); // Map<raumId, blockingEventName> für Kollisionen mit fremden Veranstaltungen
    const changedUserAvailabilities = ref(new Set());
    const changedRoomAvailabilities = ref(new Set());

    async function fetchAvailabilities(eventId) {
        const [userRes, roomRes] = await Promise.all([
            api.get(`/api/organisator/veranstaltungen/${eventId}/verfuegbarkeiten`),
            api.get(`/api/organisator/veranstaltungen/${eventId}/raeume/verfuegbarkeiten`)
        ]);

        userAvailabilities.value.clear();
        userRes.data.forEach(dto => {
            userAvailabilities.value.set(dto.nutzerId, new Set(dto.verfuegbareSlotIds));
        });

        roomAvailabilities.value.clear();
        roomBlockingInfo.value.clear();
        roomRes.data.forEach(dto => {
            roomAvailabilities.value.set(dto.raumId, new Set(dto.verfuegbareSlotIds));
            if (dto.isBlockedByOtherEvent) {
                roomBlockingInfo.value.set(dto.raumId, dto.blockingEventName);
            }
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

    // Rückgabe: nur die Nutzer, für die das Backend tatsächlich Nachbuchungs-Vorschläge geliefert
    // hat (neu verfügbar gewordener Slot + mindestens eine passende Wahlvortrag-Option) - damit
    // der Aufrufer bei wiederhergestellter Verfügbarkeit einen Nachbuchungs-Dialog anzeigen kann.
    async function saveAvailabilities(eventId) {
        const userPromises = [];
        for (const userId of changedUserAvailabilities.value) {
            const payload = {
                nutzerId: userId,
                veranstaltungId: eventId,
                verfuegbareSlotIds: Array.from(userAvailabilities.value.get(userId) || [])
            };
            userPromises.push(
                api.post(`/api/organisator/veranstaltungen/${eventId}/verfuegbarkeiten`, payload)
                    .then(res => ({ userId, vorschlaege: res.data || [] }))
            );
        }

        const roomPromises = [];
        for (const roomId of changedRoomAvailabilities.value) {
            const payload = {
                raumId: roomId,
                veranstaltungId: eventId,
                verfuegbareSlotIds: Array.from(roomAvailabilities.value.get(roomId) || [])
            };
            roomPromises.push(api.post(`/api/organisator/veranstaltungen/${eventId}/raeume/verfuegbarkeiten`, payload));
        }

        const [nutzerErgebnisse] = await Promise.all([Promise.all(userPromises), Promise.all(roomPromises)]);
        changedUserAvailabilities.value.clear();
        changedRoomAvailabilities.value.clear();

        return nutzerErgebnisse.filter(e => e.vorschlaege.some(v => v.optionen.length > 0));
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

    const isUserAvailabilityChanged = (userId) => {
        return changedUserAvailabilities.value.has(userId);
    };

    const isRoomAvailabilityChanged = (roomId) => {
        return changedRoomAvailabilities.value.has(roomId);
    };

    const getRoomBlockingEvent = (roomId) => {
        return roomBlockingInfo.value.get(roomId) ?? null;
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
        hasDirtyAvailabilities,
        isUserAvailabilityChanged,
        isRoomAvailabilityChanged,
        getRoomBlockingEvent
    };
});