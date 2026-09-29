package com.example.ui

import org.junit.Assert.*
import org.junit.Test

class PatientNotificationStateTest {
    @Test fun completing_an_older_message_does_not_clear_a_new_error() {
        val store = PatientNotificationState()
        store.post("Tentativa solicitada")
        val displayed = store.notification.value!!
        store.post("Não foi possível atualizar a fila", true)
        val latest = store.notification.value
        store.dismiss(displayed)
        assertSame(latest, store.notification.value)
        assertTrue(store.notification.value!!.isError)
    }

    @Test fun identical_messages_in_one_clock_tick_remain_distinct_events() {
        val store = PatientNotificationState()
        store.post("Falha no envio", true)
        val first = store.notification.value!!
        store.post("Falha no envio", true)
        assertNotEquals(first.id, store.notification.value!!.id)
        store.dismiss(first)
        assertNotNull(store.notification.value)
    }

    @Test fun completing_the_current_message_clears_it_and_duplicate_completion_is_harmless() {
        val store = PatientNotificationState()
        store.post("Resultado", true)
        val displayed = store.notification.value!!
        store.dismiss(displayed)
        assertNull(store.notification.value)
        store.post("Novo aviso")
        val latest = store.notification.value
        store.dismiss(displayed)
        assertSame(latest, store.notification.value)
        assertFalse(store.notification.value!!.isError)
    }

    @Test fun message_identity_is_unique_for_a_burst_without_wall_clock_dependence() {
        val store = PatientNotificationState()
        val ids = mutableSetOf<Long>()
        repeat(10000) {
            store.post("Mesmo aviso", true)
            assertTrue(ids.add(store.notification.value!!.id))
        }
    }
}
