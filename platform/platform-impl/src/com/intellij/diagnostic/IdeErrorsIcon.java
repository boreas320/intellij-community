// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.AnimatedIcon.Blinking;

import javax.swing.*;

import static com.intellij.util.ui.EmptyIcon.ICON_16;

class IdeErrorsIcon extends JLabel {
  private final boolean myEnableBlink;

  IdeErrorsIcon(boolean enableBlink) {
    myEnableBlink = enableBlink;
    setBorder(StatusBarWidget.WidgetBorder.ICON);
  }

  void setState(MessagePool.State state) {
    Icon myUnreadIcon = !myEnableBlink ? AllIcons.Ide.FatalError : new Blinking(AllIcons.Ide.FatalError);
    if (state != null && state != MessagePool.State.NoErrors) {
      setIcon(state == MessagePool.State.ReadErrors ? AllIcons.Ide.FatalError_read : myUnreadIcon);
      setToolTipText(DiagnosticBundle.message("error.notification.tooltip"));
    }
    else {
      setIcon(ICON_16);
      setToolTipText(null);
    }
  }
}