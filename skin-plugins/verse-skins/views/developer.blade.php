<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>开发者 · API Key</title>
    <style>
        :root { color-scheme: light dark; }
        * { box-sizing: border-box; }
        body {
            margin: 0; padding: 40px 16px;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
            background: #f5f6f8; color: #1f2329;
        }
        @media (prefers-color-scheme: dark) {
            body { background: #16181d; color: #e6e8eb; }
            .card { background: #1f2229; border-color: #2c313a; }
            .muted { color: #9aa1ab; }
            code, .key, textarea, input[type=text] { background: #111318; border-color: #2c313a; color: #e6e8eb; }
            .btn-secondary { background: #262a32; border-color: #343a45; color: #e6e8eb; }
            th { background: #20242b; }
            td, th { border-color: #2c313a; }
        }
        .wrap { max-width: 780px; margin: 0 auto; }
        h1 { font-size: 22px; margin: 0 0 6px; }
        h2 { font-size: 16px; margin: 0 0 10px; }
        .muted { color: #6b7280; font-size: 13px; line-height: 1.7; }
        .card { background: #fff; border: 1px solid #e5e7eb; border-radius: 14px; padding: 20px; margin-top: 18px; }
        .row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
        .spacer { flex: 1; }
        .key {
            display: block; width: 100%; margin: 12px 0; padding: 12px 14px;
            border: 1px dashed #d1d5db; border-radius: 10px; background: #f9fafb;
            font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
            font-size: 13px; word-break: break-all;
        }
        .tag { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; background: #e8f0fe; color: #1a56db; }
        .tag.off { background: #f3f4f6; color: #6b7280; }
        .tag.wait { background: #fef3c7; color: #92400e; }
        .tag.ok { background: #d1fae5; color: #065f46; }
        .tag.bad { background: #fee2e2; color: #991b1b; }
        .btn {
            appearance: none; border: 1px solid transparent; border-radius: 10px;
            padding: 9px 16px; font-size: 14px; cursor: pointer; background: #2563eb; color: #fff;
        }
        .btn:hover { filter: brightness(1.05); }
        .btn-secondary { background: #fff; border-color: #d1d5db; color: #1f2329; }
        .btn-danger { background: #dc2626; }
        .btn-sm { padding: 6px 12px; font-size: 13px; }
        form { margin: 0; }
        .notice { margin-top: 14px; padding: 12px 14px; border-radius: 10px; background: #ecfdf5; border: 1px solid #a7f3d0; color: #065f46; font-size: 13px; }
        .notice.warn { background: #fffbeb; border-color: #fde68a; color: #92400e; }
        textarea, input[type=text] {
            width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 10px;
            font-size: 14px; font-family: inherit; resize: vertical;
        }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
        th, td { text-align: left; padding: 9px 8px; border-bottom: 1px solid #e5e7eb; vertical-align: top; }
        th { background: #f9fafb; font-weight: 600; }
        ol { padding-left: 20px; margin: 10px 0 0; }
        li { margin: 4px 0; font-size: 13px; line-height: 1.7; }
        a { color: #2563eb; }
    </style>
</head>
<body>
<div class="wrap">
    <h1>开发者 · API Key</h1>
    <p class="muted">
        用 API Key 可以让启动器以你的皮肤站账号身份调用接口（把皮肤库里的皮肤应用到自己的角色）。
        Key 等同于账号凭据，请勿发给他人。为防止滥用，申请需要管理员审核。
    </p>

    @if (!empty($message))
        <div class="notice warn">{{ $message }}</div>
    @endif

    @if (!empty($issued))
        <div class="card">
            <div class="row">
                <strong>新 Key 已生成</strong>
                <span class="tag ok">只显示这一次</span>
            </div>
            <code class="key" id="issued-key">{{ $issued }}</code>
            <div class="row">
                <button class="btn" type="button" onclick="copyKey()">复制</button>
                <span class="muted" id="copy-hint"></span>
            </div>
        </div>
    @endif

    <div class="card">
        <div class="row">
            <h2 style="margin:0">当前状态</h2>
            <span class="spacer"></span>
            @php($status = $record->status ?? null)
            @if ($status === 'active')
                <span class="tag ok">已开通</span>
            @elseif ($status === 'pending')
                <span class="tag wait">等待审核</span>
            @elseif ($status === 'approved')
                <span class="tag">已批准，待生成</span>
            @elseif ($status === 'rejected')
                <span class="tag bad">未通过</span>
            @else
                <span class="tag off">未申请</span>
            @endif
        </div>

        @if ($status === null)
            @if ($isAdmin)
                <p class="muted" style="margin-top:12px">
                    你是管理员，无需审核，可以直接生成 Key。
                </p>
                <form method="post" action="/user/developer/api-key" style="margin-top:12px">
                    @csrf
                    <button class="btn" type="submit">生成 API Key</button>
                </form>
            @else
                <p class="muted" style="margin-top:12px">
                    提交申请后由管理员审核，通过后可以在本页自行生成 Key。请写清用途，方便管理员判断。
                </p>
                <form method="post" action="/user/developer/apply" style="margin-top:12px">
                    @csrf
                    <textarea name="reason" rows="3" maxlength="200"
                              placeholder="例如：我在手机上用 Verse 启动器，想把皮肤站的皮肤应用到自己的角色"></textarea>
                    <div class="row" style="margin-top:10px">
                        <button class="btn" type="submit">提交申请</button>
                        <span class="muted">至少 10 个字</span>
                    </div>
                </form>
            @endif

        @elseif ($status === 'pending')
            <p class="muted" style="margin-top:12px">
                申请已提交（{{ $record->created_at ?? '—' }}），等待管理员审核。<br>
                用途说明：{{ $record->reason ?? '—' }}
            </p>

        @elseif ($status === 'approved')
            <p class="muted" style="margin-top:12px">
                管理员已批准你的申请（{{ $record->reviewed_at ?? '—' }}）。点下面的按钮生成 Key，明文只显示这一次。
            </p>
            <form method="post" action="/user/developer/api-key" style="margin-top:12px">
                @csrf
                <button class="btn" type="submit">生成 API Key</button>
            </form>

        @elseif ($status === 'active')
            <p class="muted" style="margin-top:12px">
                Key 前缀：<code>{{ $record->key_prefix }}…</code><br>
                申请时间：{{ $record->created_at ?? '—' }}<br>
                最近使用：{{ $record->last_used_at ?? '尚未使用' }}
            </p>
            <div class="row" style="margin-top:14px">
                <form method="post" action="/user/developer/api-key/revoke"
                      onsubmit="return confirm('撤销后使用该 Key 的启动器会立即失效，确定吗？');">
                    @csrf
                    <button class="btn btn-danger" type="submit">撤销 Key</button>
                </form>
            </div>
            <p class="muted" style="margin-top:12px">撤销后需要重新提交申请。</p>

        @elseif ($status === 'rejected')
            <p class="muted" style="margin-top:12px">
                申请未通过（{{ $record->reviewed_at ?? '—' }}）。<br>
                理由：{{ $record->reason ?? '未说明' }}<br>
                24 小时后可以重新提交。
            </p>
        @endif
    </div>

    @if ($isAdmin)
        <div class="card">
            <div class="row">
                <h2 style="margin:0">待审核申请</h2>
                <span class="spacer"></span>
                <span class="muted">{{ count($pending) }} 条</span>
            </div>

            @if (count($pending) === 0)
                <p class="muted" style="margin-top:12px">暂无待审核申请。</p>
            @else
                <table>
                    <thead>
                    <tr>
                        <th style="width:56px">#</th>
                        <th>用户</th>
                        <th>用途</th>
                        <th style="width:150px">提交时间</th>
                        <th style="width:130px">操作</th>
                    </tr>
                    </thead>
                    <tbody>
                    @foreach ($pending as $item)
                        <tr>
                            <td>{{ $item->kid }}</td>
                            <td>
                                {{ $item->nickname !== '' ? $item->nickname : '（无昵称）' }}<br>
                                <span class="muted">{{ $item->email }}</span>
                            </td>
                            <td class="muted" style="max-width:280px">{{ $item->reason }}</td>
                            <td class="muted">{{ $item->created_at }}</td>
                            <td>
                                <form method="post" action="/user/developer/review/approve" style="margin-bottom:6px">
                                    @csrf
                                    <input type="hidden" name="kid" value="{{ $item->kid }}">
                                    <button class="btn btn-sm" type="submit">批准</button>
                                </form>
                                <form method="post" action="/user/developer/review/reject"
                                      onsubmit="return confirm('驳回该申请？');">
                                    @csrf
                                    <input type="hidden" name="kid" value="{{ $item->kid }}">
                                    <input type="hidden" name="note" value="未说明理由">
                                    <button class="btn btn-sm btn-secondary" type="submit">驳回</button>
                                </form>
                            </td>
                        </tr>
                    @endforeach
                    </tbody>
                </table>
            @endif
        </div>
    @endif

    <div class="card">
        <strong>怎么用</strong>
        <ol>
            <li>提交申请 → 管理员批准 → 在本页点「生成 API Key」。</li>
            <li>把 Key 填进启动器「皮肤库」右上角（只需填一次）。</li>
            <li>选中任意公开皮肤，点「应用到我的角色」。</li>
            <li>Key 与游戏账号无关：微软、离线还是外置账号进游戏都能用。</li>
        </ol>
        <p class="muted" style="margin-top:10px">
            接口说明见 <a href="/api/verse-skins">/api/verse-skins</a>。
        </p>
    </div>
</div>

<script>
    function copyKey() {
        var text = document.getElementById('issued-key').textContent.trim();
        var hint = document.getElementById('copy-hint');
        if (navigator.clipboard) {
            navigator.clipboard.writeText(text).then(function () {
                hint.textContent = '已复制';
            }, function () {
                hint.textContent = '复制失败，请手动选中';
            });
        } else {
            hint.textContent = '请手动选中复制';
        }
    }
</script>
</body>
</html>
