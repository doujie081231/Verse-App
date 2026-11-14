<?php

namespace VerseSkins\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use VerseSkins\Support\ApiKey;

/**
 * 开发者页面：申请 / 生成 / 撤销 API Key，管理员在此审核申请。
 *
 * 流程刻意做成「申请 → 管理员批准 → 用户自己生成」：
 * 批准只授予资格，Key 明文始终只出现在申请人自己的页面上，
 * 管理员既看不到也拿不到任何人的 Key。
 */
class DeveloperController
{
    public function index()
    {
        $user = Auth::user();
        if ($user === null) {
            return redirect('/auth/login');
        }

        $uid = (int) $user->uid;
        $isAdmin = (int) $user->permission >= 2;

        return response()->view('verse-skins::developer', [
            // 明文只在生成那一次通过 session 回显，刷新后不再出现
            'issued' => session('verse_api_key_issued'),
            'message' => session('verse_api_key_message'),
            'record' => ApiKey::current($uid),
            'active' => ApiKey::active($uid),
            'isAdmin' => $isAdmin,
            'pending' => $isAdmin ? ApiKey::pendingList() : [],
        ]);
    }

    /** 普通用户提交申请（写明用途，管理员据此判断） */
    public function apply(Request $request)
    {
        $user = Auth::user();
        if ($user === null) {
            return redirect('/auth/login');
        }

        $reason = trim((string) $request->input('reason', ''));
        if (mb_strlen($reason) < 10) {
            return redirect('/user/developer')
                ->with('verse_api_key_message', '请填写至少 10 个字的用途说明，方便管理员判断');
        }

        $result = ApiKey::apply((int) $user->uid, $reason);

        return redirect('/user/developer')->with('verse_api_key_message', $result['message']);
    }

    /** 生成 Key：只有管理员批准过的账号才能走到这里 */
    public function issue(Request $request)
    {
        $user = Auth::user();
        if ($user === null) {
            return redirect('/auth/login');
        }

        // 管理员直接放行：审核是给普通用户防滥用的，管理员没必要审自己
        $isAdmin = (int) $user->permission >= 2;
        $issued = ApiKey::issue((int) $user->uid, $isAdmin);
        if ($issued === null) {
            return redirect('/user/developer')
                ->with('verse_api_key_message', '当前状态不能生成 Key：需要先提交申请并由管理员审核通过');
        }

        return redirect('/user/developer')->with('verse_api_key_issued', $issued['plain']);
    }

    public function revoke(Request $request)
    {
        $user = Auth::user();
        if ($user === null) {
            return redirect('/auth/login');
        }

        ApiKey::revokeAll((int) $user->uid);

        return redirect('/user/developer')
            ->with('verse_api_key_message', 'Key 已撤销。如需继续使用，请重新提交申请。');
    }

    // ---------- 管理员审核 ----------

    public function approve(Request $request)
    {
        $admin = $this->requireAdmin();
        if ($admin === null) {
            return redirect('/auth/login');
        }

        $kid = (int) $request->input('kid');
        $ok = $kid > 0 && ApiKey::approve($kid, (int) $admin->uid);

        return redirect('/user/developer')->with(
            'verse_api_key_message',
            $ok ? '已批准该申请，用户可以自行生成 Key' : '操作失败：申请可能已被处理'
        );
    }

    public function reject(Request $request)
    {
        $admin = $this->requireAdmin();
        if ($admin === null) {
            return redirect('/auth/login');
        }

        $kid = (int) $request->input('kid');
        $note = trim((string) $request->input('note', ''));
        $ok = $kid > 0 && ApiKey::reject($kid, (int) $admin->uid, $note);

        return redirect('/user/developer')->with(
            'verse_api_key_message',
            $ok ? '已驳回该申请' : '操作失败：申请可能已被处理'
        );
    }

    /** 管理员判定：Blessing Skin 用 users.permission ≥ 2 表示管理员 */
    private function requireAdmin()
    {
        $user = Auth::user();
        if ($user === null || (int) $user->permission < 2) {
            return null;
        }

        return $user;
    }
}
