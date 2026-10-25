#!/usr/bin/env python3
"""检查模块方向、第三方/JNI 边界以及已删除启动链的回流。无需 Gradle 或 Android SDK。"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
THIRD_PARTY = r'(?:com\.tungsten|com\.mio|net\.kdt|org\.lwjgl|org\.libsdl|com\.verse\.explorer\.(?:jvmlauncher|launcher\.(?:bridge|fcl)))'


def code_only(text):
    # 同时识别字符串、字符及注释，避免 URL、日志和注释形成假依赖。
    pattern = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(pattern, lambda m: '\n' * m.group().count('\n') or ' ', text)


def boundary_references(text):
    code = code_only(text)
    names = set(re.findall(r'\b' + THIRD_PARTY + r'(?:\.[A-Za-z_$][\w$]*)+', code))
    names.update('JNI:' + value for value in re.findall(r'\b(?:[\w.]+\.)?native[A-Z]\w*(?=\s*\()', code))
    if re.search(r'\bSystem\.load(?:Library)?\s*\(', code):
        names.add('JNI:System.load')
    if re.search(r'\bexternal\s+fun\b|\bnative\s+\w+\s+\w+\s*\(', code):
        names.add('JNI:declaration')
    return names


def module_graph(root):
    graph = {}
    for directory in root.iterdir():
        if not directory.is_dir() or directory.name.startswith('.'):
            continue
        for name in ('build.gradle.kts', 'build.gradle'):
            path = directory / name
            if path.exists():
                graph[directory.name] = set(re.findall(r'project\s*\(\s*["\']:(\w+)["\']\s*\)', path.read_text(encoding='utf-8')))
    return graph


def graph_errors(graph):
    errors, visiting, done = [], [], set()

    def visit(module):
        if module in visiting:
            errors.append('循环依赖: ' + ' -> '.join(visiting + [module]))
            return
        if module in done:
            return
        visiting.append(module)
        for dependency in sorted(graph.get(module, ())):
            visit(dependency)
        visiting.pop()
        done.add(module)

    for module in sorted(graph):
        visit(module)
    if 'FclCore' in graph.get('VerseCore', set()):
        errors.append('VerseCore 不得依赖 FclCore')
    if graph.get('VerseCore', set()) & {'VerseApp', 'VerseLauncher', 'JvmLauncher'}:
        errors.append('VerseCore 只能定义能力端口，不得依赖应用或启动实现')
    if 'VerseApp' in graph.get('VerseLauncher', set()):
        errors.append('VerseLauncher 不得反向依赖 VerseApp')
    return errors


def check(root=ROOT):
    exceptions = json.loads((root / 'tools/architecture-compatibility.json').read_text(encoding='utf-8'))['files']
    errors = graph_errors(module_graph(root))
    observed = set()
    retired = re.compile(r'\bGameService\b|\bGameLaunchParams\b|com\.verse\.explorer\.launcher\.launch\.(?:MinecraftLauncher|LaunchConfiguration|LaunchProcess|LaunchCallback|ClasspathBuilder|JvmArgsBuilder|GameArgsParser|LaunchEnvironmentSetup)\b')
    for module in ('VerseCore', 'VerseApp', 'VerseLauncher'):
        paths = list((root / module / 'src/main').rglob('*')) + list((root / module).glob('*.pro'))
        for path in paths:
            if path.suffix not in {'.kt', '.java', '.xml', '.cpp', '.c', '.h', '.pro'}:
                continue
            relative = path.relative_to(root).as_posix()
            text = path.read_text(encoding='utf-8')
            protocol_text = text if path.suffix in {'.xml', '.pro', '.cpp', '.c', '.h'} else code_only(text)
            if retired.search(protocol_text):
                errors.append(relative + ': 已删除启动入口不得恢复')
            if module == 'VerseApp' and re.search(r'\b(?:AccountsViewModel|VersionsViewModel|ResourceViewModel)\.shared\s*\(|\bGlobalScope\b|\bVerseApplication\.currentActivity\b', code_only(text)):
                errors.append(relative + ': 页面生命周期或任务作用域回退')
            references = boundary_references(text)
            if module == 'VerseCore' and references:
                errors.append(relative + ': Core 越层引用 ' + ', '.join(sorted(references)))
                continue
            allowed = set(exceptions.get(relative, {}).get('references', []))
            if references - allowed:
                errors.append(relative + ': 白名单外的第三方/JNI 引用 ' + ', '.join(sorted(references - allowed)))
            if relative in exceptions:
                observed.add(relative)
    for stale in exceptions.keys() - observed:
        errors.append(stale + ': 文件已删除，请移除过期兼容记录')
    return errors


if __name__ == '__main__':
    failures = check()
    for failure in failures:
        print(failure)
    print(f'Architecture: {len(failures)} violation(s)')
    sys.exit(bool(failures))
