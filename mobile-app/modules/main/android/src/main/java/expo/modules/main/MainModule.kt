package expo.modules.main

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MainModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("Main")

    View(MainView::class) {
    }
  }
}
