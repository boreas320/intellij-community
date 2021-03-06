// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLog
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.util.VcsLogUtil

class CompareRevisionsFromLogAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val log = e.getData(VcsLogDataKeys.VCS_LOG)
    val handler = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER)
    if (log == null || handler == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val commits = log.selectedCommits
    
    e.presentation.isVisible = commits.size == 2
    e.presentation.isEnabled = commits.first().root == commits.last().root
  }

  override fun actionPerformed(e: AnActionEvent) {
    val log = e.getRequiredData(VcsLogDataKeys.VCS_LOG)
    val handler = e.getRequiredData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER)

    VcsLogUsageTriggerCollector.triggerUsage(e, this)

    val commits = log.selectedCommits
    if (commits.size == 2) {
      val root = commits.first().root
      handler.showDiffForPaths(root, VcsLogUtil.getAffectedPaths(root, e), commits[1].hash, commits[0].hash)
    }
  }

}