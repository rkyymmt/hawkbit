/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.dstag;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.List;
import java.util.Optional;

import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TagManagement;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.model.DistributionSetTag;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.colorpicker.ColorPickerConstants;
import org.eclipse.hawkbit.ui.colorpicker.ColorPickerHelper;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEventType;
import org.eclipse.hawkbit.ui.components.RefreshableContainer;
import org.eclipse.hawkbit.ui.layouts.AbstractCreateUpdateTagLayout;
import org.eclipse.hawkbit.ui.management.event.DistributionSetTagTableEvent;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUILabelDefinitions;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.ui.UI;

/**
 * Class for Create/Update Tag Layout of distribution set
 */
public class CreateUpdateDistributionTagLayoutWindow extends AbstractCreateUpdateTagLayout<DistributionSetTag>
        implements RefreshableContainer {

    private static final long serialVersionUID = 444276149954167545L;

    private static final String TARGET_TAG_NAME_DYNAMIC_STYLE = "new-target-tag-name";
    private static final String MSG_TEXTFIELD_NAME = "textfield.name";

    CreateUpdateDistributionTagLayoutWindow(final VaadinMessageSource i18n, final TagManagement tagManagement,
            final EntityFactory entityFactory, final UIEventBus eventBus, final SpPermissionChecker permChecker,
            final UINotification uiNotification) {
        super(i18n, tagManagement, entityFactory, eventBus, permChecker, uiNotification);
    }

    @Override
    protected void populateTagNameCombo() {
        tagNameComboBox.removeAllItems();
        final List<DistributionSetTag> distTagNameList = tagManagement.findAllDistributionSetTags();
        distTagNameList.forEach(value -> tagNameComboBox.addItem(value.getName()));
    }

    @Override
    protected void addListeners() {
        super.addListeners();
        optiongroup.addValueChangeListener(this::optionValueChanged);
    }

    @Override
    protected void createEntity() {
        createNewTag();

    }

    @Override
    protected void updateEntity(final DistributionSetTag entity) {
        updateExistingTag(findEntityByName()
                .orElseThrow(() -> new EntityNotFoundException(DistributionSetTag.class, tagName.getValue())));
    }

    @Override
    protected Optional<DistributionSetTag> findEntityByName() {
        return tagManagement.findDistributionSetTag(tagName.getValue());
    }

    /**
     * Create new tag.
     */
    @Override
    protected void createNewTag() {
        super.createNewTag();
        final String tagNameValueTrimmed = HawkbitCommonUtil.trimAndNullIfEmpty(tagNameValue);
        final String tagDescriptionTrimmed = HawkbitCommonUtil.trimAndNullIfEmpty(tagDescValue);
        if (isNotEmpty(tagNameValueTrimmed)) {

            String colour = ColorPickerConstants.START_COLOR.getCSS();
            if (isNotEmpty(getColorPicked())) {
                colour = getColorPicked();
            }

            final DistributionSetTag newDistTag = tagManagement.createDistributionSetTag(entityFactory.tag().create()
                    .name(tagNameValueTrimmed).description(tagDescriptionTrimmed).colour(colour));
            eventBus.publish(this, new DistributionSetTagTableEvent(BaseEntityEventType.ADD_ENTITY, newDistTag));
            displaySuccess(newDistTag.getName());
            resetDistTagValues();
        } else {
            displayValidationError(i18n.getMessage(SPUILabelDefinitions.MISSING_TAG_NAME));
        }
    }

    /**
     * RESET.
     */
    @Override
    public void discard() {
        super.discard();
        resetDistTagValues();
    }

    /**
     * RESET.
     */
    private void resetDistTagValues() {
        tagName.removeStyleName(TARGET_TAG_NAME_DYNAMIC_STYLE);
        tagName.addStyleName(SPUIDefinitions.NEW_TARGET_TAG_NAME);
        tagName.setValue("");
        tagName.setInputPrompt(i18n.getMessage(MSG_TEXTFIELD_NAME));
        setColor(ColorPickerConstants.START_COLOR);
        getWindow().setVisible(false);
        tagPreviewBtnClicked = false;
        UI.getCurrent().removeWindow(getWindow());
    }

    /**
     * Select tag & set tag name & tag desc values corresponding to selected
     * tag.
     *
     * @param distTagSelected
     *            as the selected tag from combo
     */
    @Override
    public void setTagDetails(final String distTagSelected) {
        tagName.setValue(distTagSelected);
        final Optional<DistributionSetTag> selectedDistTag = tagManagement.findDistributionSetTag(distTagSelected);
        if (selectedDistTag.isPresent()) {
            tagDesc.setValue(selectedDistTag.get().getDescription());
            if (null == selectedDistTag.get().getColour()) {
                setTagColor(getColorPickerLayout().getDefaultColor(), ColorPickerConstants.DEFAULT_COLOR);
            } else {
                setTagColor(ColorPickerHelper.rgbToColorConverter(selectedDistTag.get().getColour()),
                        selectedDistTag.get().getColour());
            }
        }
    }

    @Override
    protected void createRequiredComponents() {
        super.createRequiredComponents();
        createOptionGroup(permChecker.hasCreateDistributionPermission(), permChecker.hasUpdateDistributionPermission());
    }

    @Override
    protected void reset() {

        super.reset();
        setOptionGroupDefaultValue(permChecker.hasCreateDistributionPermission(),
                permChecker.hasUpdateDistributionPermission());
    }

    @Override
    protected String getWindowCaption() {
        return i18n.getMessage("caption.add.tag");
    }

    @Override
    public void refreshContainer() {
        populateTagNameCombo();
    }
}
