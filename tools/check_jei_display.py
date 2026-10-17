#!/usr/bin/env python3
"""Static display/wiring check for the Calculator JEI integration (no Minecraft/Gradle/JEI needed).

Faithful copy of the script recorded in notes/feedback-jei.md section 9, kept as a real tool so
scripts/Validate-Feedback.ps1 can execute it instead of trusting the note.
"""
import json, os, re, sys

ROOT = r"F:\ai\calculator-port"
CALC = os.path.join(ROOT, "calculator")
RECIPE_SRC = os.path.join(CALC, "src/main/java/sonar/calculator/mod/recipe/RecipeContent.java")
PLUGIN = os.path.join(CALC, "src/main/java/sonar/calculator/mod/integration/jei/CalculatorJeiPlugin.java")
JEI_GRADLE = os.path.join(CALC, "jei.gradle")
MACHINE_DATA = os.path.join(CALC, "src/main/resources/data/calculator/recipes/machine")
JAVA_ROOT = os.path.join(CALC, "src/main/java/sonar/calculator/mod")
failures = []


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def machine_types():
    match = re.search(r"MACHINE_TYPES\s*=\s*List\.of\((.*?)\);", read(RECIPE_SRC), re.S)
    if not match:
        failures.append("RecipeContent.MACHINE_TYPES not found")
        return []
    return re.findall(r'"([^"]+)"', match.group(1))


def recipe_count(machine_type):
    directory = os.path.join(MACHINE_DATA, machine_type)
    return len([n for n in os.listdir(directory) if n.endswith(".json")]) if os.path.isdir(directory) else 0


def main():
    types = machine_types()
    print("machine recipe families declared: %d" % len(types))
    plugin = read(PLUGIN)
    types_source = read(os.path.join(CALC, "src/main/java/sonar/calculator/mod/integration/jei/JeiRecipeTypes.java"))
    if "@JeiPlugin" not in plugin:
        failures.append("CalculatorJeiPlugin is not annotated with @JeiPlugin")
    if "implements IModPlugin" not in plugin:
        failures.append("CalculatorJeiPlugin does not implement IModPlugin")
    print("\n%-16s %-8s %-10s %-10s" % ("family", "recipes", "type", "category"))
    total = 0
    for machine_type in types:
        count = recipe_count(machine_type)
        total += count
        has_type = ('"%s"' % machine_type) in types_source
        declared = ('"%s"' % machine_type) in plugin
        if count == 0:
            failures.append("family %s has no data-pack recipe (category would be empty)" % machine_type)
        if not declared:
            failures.append("family %s has no JEI category in CalculatorJeiPlugin" % machine_type)
        print("%-16s %-8d %-10s %-10s" % (machine_type, count, has_type, declared))
    print("total machine recipes: %d" % total)
    if "research" in [t.lower() for t in types]:
        failures.append("research leaked into MACHINE_TYPES")
    research_files = [os.path.join(b, n) for b, _d, f in
                      os.walk(os.path.join(CALC, "src/main/resources/data/calculator/recipes"))
                      for n in f if "research" in n.lower()]
    if research_files:
        failures.append("research recipe data present: %s" % research_files)
    gradle = read(JEI_GRADLE)
    for line in gradle.splitlines():
        stripped = line.strip()
        if stripped.startswith("implementation ") or stripped.startswith("api "):
            failures.append("JEI declared with a packaging scope: %s" % stripped)
    if "compileOnly" not in gradle or "runtimeOnly" not in gradle:
        failures.append("jei.gradle must use compileOnly and runtimeOnly")
    version = re.search(r"jei_version\s*=.*?'([0-9][^']*)'", gradle)
    print("\npinned JEI version: %s" % (version.group(1) if version else "NOT PINNED"))
    if not version:
        failures.append("jei.gradle does not pin a JEI version")
    jars = []
    for b, _d, f in os.walk(CALC):
        low = b.lower()
        if os.sep + "build" in low or os.sep + ".gradle" in low:
            continue
        jars += [os.path.join(b, n) for n in f if n.lower().endswith(".jar")]
    if jars:
        failures.append("third-party jar present in the module sources: %s" % jars)
    print("bundled jar files in calculator module sources: %d" % len(jars))
    integration_dir = os.path.join(JAVA_ROOT, "integration", "jei")
    offenders = [os.path.join(b, n) for b, _d, f in os.walk(JAVA_ROOT)
                 if not os.path.normpath(b).startswith(os.path.normpath(integration_dir))
                 for n in f if n.endswith(".java") and "integration.jei" in read(os.path.join(b, n))]
    if offenders:
        failures.append("integration package referenced outside itself: %s" % offenders)
    print("common/server files referencing integration.jei: %d" % len(offenders))

    # --- Machine-artwork layouts (this update): every category must crop real GUI artwork. ------
    layout_src = read(os.path.join(CALC, "src/main/java/sonar/calculator/mod/integration/jei/JeiLayout.java"))
    category_src = read(os.path.join(CALC, "src/main/java/sonar/calculator/mod/integration/jei/MachineRecipeCategory.java"))
    gui_dir = os.path.join(CALC, "src/main/resources/assets/calculator/textures/gui")
    family_blocks = plugin.split("new Family(")[1:]
    if len(family_blocks) != len(types):
        failures.append("JEI family count %d does not match MACHINE_TYPES %d" % (len(family_blocks), len(types)))
    for block in family_blocks:
        match = re.match(r'\s*"([^"]+)"', block)
        family = match.group(1) if match else "?"
        lists = re.findall(r'List\.of\(([^)]*)\)', block)
        if len(lists) < 2 or not any(part.strip() for part in lists[:2]):
            failures.append("family %s has no non-empty catalyst list" % family)
        layout = re.search(r'JeiLayout\.(\w+)\(([^)]*)\)', block)
        if not layout:
            failures.append("family %s does not use a machine-artwork JeiLayout" % family)
        elif layout.group(1) in ("value", "outputOnly"):
            failures.append("family %s still uses the old blank/value layout factory" % family)
    for name in set(re.findall(r'"([a-zA-Z0-9_]+\.png)"', plugin + layout_src)):
        if not os.path.exists(os.path.join(gui_dir, name)):
            failures.append("JEI layout references missing GUI texture: %s" % name)
    if "setTextureSize(256, 256)" not in category_src:
        failures.append("JEI background/arrow drawables must sample legacy 256px GUI coordinates")
    if "createBlankDrawable" in category_src:
        failures.append("JEI category still uses a blank background instead of machine artwork")
    if ".isValid()" not in plugin or "x + SLOT <= width" not in layout_src \
            or "y + SLOT <= height" not in layout_src:
        failures.append("JEI layout bounds are not validated")
    if "playerInventoryTitle" in category_src or "formatStorage" in category_src:
        failures.append("JEI category draws a machine title/player inventory/energy value")
    for token in ("display.setCount(input.count())", "display.setTag(input.exactNbt())",
                  "slot.addItemStack(outputs.get(i))", "chanceBound(index)", "choiceCount(index)"):
        if token not in category_src:
            failures.append("JEI recipe detail lost: %s" % token)

    # --- Category/tooltip language keys must exist in every shipped language. ------------------
    lang_dir = os.path.join(CALC, "src/main/resources/assets/calculator/lang")
    lang_sets = {}
    for name in ("en_us.json", "zh_cn.json", "ru_ru.json", "uk_ua.json"):
        data = json.loads(read(os.path.join(lang_dir, name)))
        lang_sets[name] = {key for key in data if key.startswith("jei.calculator.")}
    baseline = lang_sets["en_us.json"]
    for name, keys in lang_sets.items():
        if keys != baseline:
            failures.append("JEI language keys differ in %s: missing=%s extra=%s"
                            % (name, sorted(baseline - keys), sorted(keys - baseline)))
        for machine_type in types:
            if "jei.calculator.category." + machine_type not in keys:
                failures.append("missing JEI category title key %s in %s" % (machine_type, name))

    # --- Machine UI: no Inventory caption; conductor status restored from synced state. --------
    screen_src = read(os.path.join(CALC, "src/main/java/sonar/calculator/mod/client/screen/MachineScreen.java"))
    if "playerInventoryTitle" in screen_src:
        failures.append("MachineScreen still draws the Inventory caption")
    for token in ('conductorStatus(g)', 'conductor.wait', 'centredLabel(g, status, 46, 0x404040)',
                  'getStateValue("lightningDelay")', 'getStateValue("lightningTimer")',
                  'getStateValue("lightningRandom")'):
        if token not in screen_src:
            failures.append("conductor status rendering lost: %s" % token)
    status_src = read(os.path.join(CALC, "src/main/java/sonar/calculator/mod/machine/ConductorStatus.java"))
    for token in ("delay < MESSAGE_MIN_DELAY || timer >= MESSAGE_MAX_TIMER",
                  "random < 0 || random >= MESSAGE_COUNT",
                  '"conductor.msg" + random'):
        if token not in status_src:
            failures.append("conductor status branch lost: %s" % token)
    menu_src = read(os.path.join(CALC, "src/main/java/sonar/calculator/mod/machine/MachineMenu.java"))
    for token in ('"lightningTimer", "lightningDelay", "lightningRandom"', "dataHalf(", "joinDataHalves("):
        if token not in menu_src:
            failures.append("conductor 32-bit menu sync lost: %s" % token)
    env_src = read(os.path.join(CALC, "src/main/java/sonar/calculator/mod/machine/MachineEnvironment.java"))
    if 'state.putInt("lightningRandom"' not in env_src:
        failures.append("conductor wait-message index is never rolled")

    print("\n" + ("FAILURES:\n  " + "\n  ".join(failures) if failures else "ALL CHECKS PASSED"))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
