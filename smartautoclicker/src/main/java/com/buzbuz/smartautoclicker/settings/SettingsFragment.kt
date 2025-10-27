/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setChecked
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnClickListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle

import com.buzbuz.smartautoclicker.databinding.FragmentSettingsBinding

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var viewBinding: FragmentSettingsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = FragmentSettingsBinding.inflate(inflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fieldShowScenarioFilters.apply {
            setTitle(requireContext().getString(R.string.field_show_scenario_filters_ui_title))
            setDescription(requireContext().getString(R.string.field_show_scenario_filters_ui_desc))
            setOnClickListener(viewModel::toggleScenarioFiltersUi)
        }

        viewBinding.fieldLegacyActionsUi.apply {
            setTitle(requireContext().getString(R.string.field_legacy_action_ui_title))
            setDescription(requireContext().getString(R.string.field_legacy_action_ui_desc))
            setOnClickListener(viewModel::toggleLegacyActionUi)
        }

        viewBinding.fieldLegacyNotificationUi.apply {
            setTitle(requireContext().getString(R.string.field_legacy_notification_ui_title))
            setDescription(requireContext().getString(R.string.field_legacy_notification_ui_desc))
            setOnClickListener(viewModel::toggleLegacyNotificationUi)
        }

        viewBinding.fieldForceEntireScreen.apply {
            setTitle(requireContext().getString(R.string.field_force_entire_screen_title))
            setDescription(requireContext().getString(R.string.field_force_entire_screen_desc))
            setOnClickListener(viewModel::toggleForceEntireScreenCapture)
        }

        viewBinding.fieldInputBlockWorkaround.apply {
            setTitle(requireContext().getString(R.string.field_input_block_workaround_title))
            setDescription(requireContext().getString(R.string.field_input_block_workaround_desc))
            setOnClickListener(viewModel::toggleInputBlockWorkaround)
        }


        viewBinding.fieldTroubleshooting.apply {
            setTitle(requireContext().getString(R.string.field_troubleshooting))
            setOnClickListener { viewModel.showTroubleshootingDialog(requireActivity()) }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isScenarioFiltersUiEnabled.collect(viewBinding.fieldShowScenarioFilters::setChecked) }
                launch { viewModel.isLegacyActionUiEnabled.collect(viewBinding.fieldLegacyActionsUi::setChecked) }
                launch { viewModel.isLegacyNotificationUiEnabled.collect(viewBinding.fieldLegacyNotificationUi::setChecked) }
                launch { viewModel.isEntireScreenCaptureForced.collect(viewBinding.fieldForceEntireScreen::setChecked) }
                launch { viewModel.isInputWorkaroundEnabled.collect(viewBinding.fieldInputBlockWorkaround::setChecked) }
                launch { viewModel.shouldShowInputBlockWorkaround.collect(::updateInputBlockWorkaroundVisibility) }
                launch { viewModel.shouldShowEntireScreenCapture.collect(::updateForceEntireScreenVisibility) }
            }
        }
    }

    private fun updateForceEntireScreenVisibility(shouldBeVisible: Boolean) {
        if (shouldBeVisible) {
            viewBinding.dividerForceEntireScreen.visibility = View.VISIBLE
            viewBinding.fieldForceEntireScreen.root.visibility = View.VISIBLE
        } else {
            viewBinding.dividerForceEntireScreen.visibility = View.GONE
            viewBinding.fieldForceEntireScreen.root.visibility = View.GONE
        }
    }

    private fun updateInputBlockWorkaroundVisibility(shouldBeVisible: Boolean) {
        if (shouldBeVisible) {
            viewBinding.dividerInputBlockWorkaround.visibility = View.VISIBLE
            viewBinding.fieldInputBlockWorkaround.root.visibility = View.VISIBLE
        } else {
            viewBinding.dividerInputBlockWorkaround.visibility = View.GONE
            viewBinding.fieldInputBlockWorkaround.root.visibility = View.GONE
        }
    }

}