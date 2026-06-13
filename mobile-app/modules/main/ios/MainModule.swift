import ExpoModulesCore

public class MainModule: Module {
  public func definition() -> ModuleDefinition {
    Name("Main")

    View(MainView.self) {
    }
  }
}
