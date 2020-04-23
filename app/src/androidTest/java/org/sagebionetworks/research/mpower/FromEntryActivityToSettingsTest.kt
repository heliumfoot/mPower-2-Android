/*
 * BSD 3-Clause License
 *
 * Copyright 2020  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.research.mpower

import android.R
import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.sagebionetworks.research.mpower.R.id

@LargeTest
@RunWith(AndroidJUnit4::class)
class FromEntryActivityToSettingsTest {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityTestRule(EntryActivity::class.java)
    var skipSingInPart = false

    @Before
    fun setupTest() {
        try {
            //check that user is not signed in. If he is, skip sing in part
            val appCompatButton = Espresso.onView(
                    Matchers.allOf(ViewMatchers.withId(id.button_go_forward), ViewMatchers.withText("Next"),
                            childAtPosition(
                                    childAtPosition(
                                            ViewMatchers.withId(id.bp_next_button_container),
                                            0),
                                    2),
                            ViewMatchers.isDisplayed()))

            appCompatButton.perform(ViewActions.click())
        } catch (ex: NoMatchingViewException) {
            skipSingInPart = true
        }
    }

    @Test
    fun entryActivityTest() {
        if (!skipSingInPart) {

            val appCompatButton = Espresso.onView(
                    Matchers.allOf(ViewMatchers.withId(id.button_go_forward), ViewMatchers.withText("Next"),
                            childAtPosition(
                                    childAtPosition(
                                            ViewMatchers.withId(id.bp_next_button_container),
                                            0),
                                    2),
                            ViewMatchers.isDisplayed()))
            appCompatButton.perform(ViewActions.click())

            val appCompatTextView = Espresso.onView(
                    Matchers.allOf(ViewMatchers.withId(id.internal_sign_in_link),
                            ViewMatchers.withText("Click for External ID login [TEST BUILD]"),
                            childAtPosition(
                                    Matchers.allOf(ViewMatchers.withId(id.mp_root_instruction_layout),
                                            childAtPosition(
                                                    ViewMatchers.withId(id.mp_step_layout_container),
                                                    0)),
                                    5),
                            ViewMatchers.isDisplayed()))
            appCompatTextView.perform(ViewActions.click())

            Thread.sleep(1000)

            val appCompatEditText = Espresso.onView(
                    Matchers.allOf(ViewMatchers.withId(id.firstName),
                            childAtPosition(
                                    childAtPosition(
                                            ViewMatchers.withId(R.id.content),
                                            0),
                                    2),
                            ViewMatchers.isDisplayed()))
            appCompatEditText.perform(ViewActions.replaceText("ella"), ViewActions.closeSoftKeyboard())

            val appCompatEditText2 = Espresso.onView(
                    Matchers.allOf(ViewMatchers.withId(id.externalId),
                            childAtPosition(
                                    childAtPosition(
                                            ViewMatchers.withId(R.id.content),
                                            0),
                                    4),
                            ViewMatchers.isDisplayed()))
            appCompatEditText2.perform(ViewActions.replaceText("HF20200318C"), ViewActions.closeSoftKeyboard())

            val appCompatCheckBox = Espresso.onView(
                    Matchers.allOf(ViewMatchers.withId(id.skipConsent), ViewMatchers.withText("Skip Consent"),
                            childAtPosition(
                                    childAtPosition(
                                            ViewMatchers.withId(R.id.content),
                                            0),
                                    5),
                            ViewMatchers.isDisplayed()))
            appCompatCheckBox.perform(ViewActions.click())

            val appCompatButton2 = Espresso.onView(
                    Matchers.allOf(ViewMatchers.withId(id.signIn), ViewMatchers.withText("Sign In"),
                            childAtPosition(
                                    childAtPosition(
                                            ViewMatchers.withId(R.id.content),
                                            0),
                                    6),
                            ViewMatchers.isDisplayed()))
            appCompatButton2.perform(ViewActions.click())

            Thread.sleep(7000)
        }

        val bottomNavigationItemView = Espresso.onView(
                Matchers.allOf(ViewMatchers.withId(id.navigation_profile),
                        ViewMatchers.withContentDescription("Profile"),
                        childAtPosition(
                                childAtPosition(
                                        ViewMatchers.withId(id.navigation),
                                        0),
                                2),
                        ViewMatchers.isDisplayed()))
        bottomNavigationItemView.perform(ViewActions.click())

        val appCompatImageView = Espresso.onView(
                Matchers.allOf(ViewMatchers.withId(id.settings_icon),
                        childAtPosition(
                                childAtPosition(
                                        ViewMatchers.withId(id.fragment_container),
                                        0),
                                2),
                        ViewMatchers.isDisplayed()))
        appCompatImageView.perform(ViewActions.click())

        Thread.sleep(1000)

         val constraintLayout = Espresso.onView(
                 Matchers.allOf(ViewMatchers.withId(id.background),
                         childAtPosition(
                                 Matchers.allOf(ViewMatchers.withId(id.list),
                                         childAtPosition(
                                                 ViewMatchers.withClassName(
                                                         Matchers.`is`("android.widget.RelativeLayout")),
                                                 3)),
                                 2),
                         ViewMatchers.isDisplayed()))
         constraintLayout.perform(ViewActions.click())

         Thread.sleep(1000)

         val appCompatButton3 = Espresso.onView(
                 Matchers.allOf(ViewMatchers.withId(id.radio_okay), ViewMatchers.withText("Okay"),
                         ViewMatchers.isDisplayed()))

         appCompatButton3.perform(ViewActions.click())

         val appCompatButton4 = Espresso.onView(
                 Matchers.allOf(ViewMatchers.withId(id.done_button), ViewMatchers.withText("Save"),
                         childAtPosition(
                                 childAtPosition(
                                         ViewMatchers.withId(R.id.content),
                                         0),
                                 5),
                         ViewMatchers.isDisplayed()))
         appCompatButton4.perform(ViewActions.click())
         Thread.sleep(1000)

       // checkOnEnable()
    }

    fun checkOnEnable() {
        val constraintLayout = Espresso.onView(
                Matchers.allOf(ViewMatchers.withId(id.background),
                        childAtPosition(
                                Matchers.allOf(ViewMatchers.withId(id.list),
                                        childAtPosition(
                                                ViewMatchers.withClassName(
                                                        Matchers.`is`("android.widget.RelativeLayout")),
                                                3)),
                                2),
                        ViewMatchers.isDisplayed()))
        constraintLayout.perform(ViewActions.click())

        val appCompatButton3 = Espresso.onView(
                Matchers.allOf(ViewMatchers.withId(id.radio_okay), ViewMatchers.withText("Okay"),
                        ViewMatchers.isDisplayed()))

        appCompatButton3.perform(ViewActions.click())

        val appCompatButton4 = Espresso.onView(
                Matchers.allOf(ViewMatchers.withId(id.done_button), ViewMatchers.withText("Save"),
                        childAtPosition(
                                childAtPosition(
                                        ViewMatchers.withId(R.id.content),
                                        0),
                                5),
                        ViewMatchers.isDisplayed()))
        appCompatButton4.perform(ViewActions.click())
        Thread.sleep(7000)

        val constraintLayout2 = Espresso.onView(
                Matchers.allOf(
                        childAtPosition(
                                Matchers.allOf(ViewMatchers.withId(id.list),
                                        childAtPosition(Matchers.allOf(
                                                ViewMatchers.withId(id.background),
                                                Matchers.allOf(childAtPosition(ViewMatchers.withClassName(
                                                        Matchers.`is`("android.widget.TextView")),
                                                        /*withText("Enabled"),*/
                                                        1
                                                ))),
                                                /*  ViewMatchers.withClassName(
                                                          Matchers.`is`("android.widget.TextView")),*/
                                                3)),
                                2),
                        isDisplayed()))

        constraintLayout2.check(matches(isDisplayed()))
    }

    /*  fun checkOnDisable(){

         val appCompatButton5 = Espresso.onView(
                        Matchers.allOf(ViewMatchers.withId(id.radio_not_now), ViewMatchers.withText("Not Now"),
                                   childAtPosition(
                                        childAtPosition(
                                                ViewMatchers.withId(R.id.content),
                                                1),
                                        4),
                          ViewMatchers.isDisplayed()))

          appCompatButton5.perform(ViewActions.click())
      }*/

    private fun childAtPosition(
            parentMatcher: Matcher<View>, position: Int): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                        && view == parent.getChildAt(position)
            }
        }
    }
}
