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
    print("\n" + ("FAILURES:\n  " + "\n  ".join(failures) if failures else "ALL CHECKS PASSED"))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
