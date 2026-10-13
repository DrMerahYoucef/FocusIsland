package com.example

import android.app.Application
import com.example.ui.screen.auth.AuthViewModel
import com.example.ui.screen.auth.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sign-in form rules. Robolectric provides android.util.Patterns; a plain Application avoids Firebase. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AuthValidationTest {

    @Test
    fun `email is required and must look like an address`() {
        assertEquals(FieldError.EMAIL_REQUIRED, AuthViewModel.validateEmail("   "))
        assertEquals(FieldError.EMAIL_INVALID, AuthViewModel.validateEmail("sara@"))
        assertEquals(FieldError.EMAIL_INVALID, AuthViewModel.validateEmail("sara.example.com"))
        assertNull(AuthViewModel.validateEmail(" sara@example.com "))
    }

    @Test
    fun `password length is only enforced when creating an account`() {
        assertEquals(FieldError.PASSWORD_REQUIRED, AuthViewModel.validatePassword("", creating = false))
        assertNull(AuthViewModel.validatePassword("123", creating = false))
        assertEquals(FieldError.PASSWORD_SHORT, AuthViewModel.validatePassword("12345", creating = true))
        assertNull(AuthViewModel.validatePassword("123456", creating = true))
    }

    @Test
    fun `display name is trimmed and bounded`() {
        assertEquals(FieldError.NAME_REQUIRED, AuthViewModel.validateName("  "))
        assertEquals(FieldError.NAME_SHORT, AuthViewModel.validateName(" S "))
        assertEquals(FieldError.NAME_LONG, AuthViewModel.validateName("x".repeat(AuthViewModel.MAX_NAME + 1)))
        assertNull(AuthViewModel.validateName("Sara"))
    }
}
