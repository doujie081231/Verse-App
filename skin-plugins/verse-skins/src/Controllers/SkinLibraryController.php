<?php

namespace VerseSkins\Controllers;

use App\Models\Player;
use App\Models\Texture;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use VerseSkins\Support\ApiKey;
use Yggdrasil\Models\Token;

/**
 * 皮肤库接口。
 *
 * 契约（任何启动器都可接入）：
 *
 *   GET /api/verse-skins?page=1&limit=24&sort=new|likes
 *   200 {
 *     "page": 1, "limit": 24, "total": 78,
 *     "items": [
 *       { "tid": 12, "name": "皮肤名", "model": "classic|slim",
 *         "url": "https://skin.example.com/textures/<hash>",
 *         "likes": 3, "uploaded_at": "2026-11-01 12:00:00" }
 *     ]
 *   }
 *
 *   POST /api/verse-skins/apply
 *   Authorization: Bearer <Yggdrasil accessToken>
 *   { "tid": 12, "uuid": "可选，省略时用该账号第一个角色" }
 *   200 { "ok": true, "player": "角色名", "uuid": "...", "tid": 12,
 *         "model": "classic", "skin_url": "..." }
 *
 * 安全约束：
 *  - 列表只读取 public=1 的皮肤，不返回上传者账号等任何隐私字段；
 *  - 应用皮肤必须携带 accessToken，且只能作用于 token 所属账号名下的角色；
 *  - 只允许使用库里已有的公开皮肤，服务端不接收任意上传文件，避免被当作图床或
 *    绕过站点的图片尺寸/内容校验。
 */
class SkinLibraryController
{
    /** 单页上限，防止一次拉全站数据 */
    private const MAX_LIMIT = 48;

    /** 皮肤站用 steve / alex 区分模型，两者都是皮肤 */
    private const SKIN_TYPES = ['steve', 'alex'];

    public function index(Request $request)
    {
        // 列表也要 key（和 CurseForge 的 x-api-key 一致）：应用级 key 或账号级 key 都可以。
        // 这样第三方接接口必须先申请，运维也能按 key 看调用量、按 key 限流。
        if ($this->keyRecord($request) === null) {
            return response()->json([
                'error' => 'unauthorized',
                'message' => '缺少或无效的 API Key：请在皮肤站「开发者中心」申请后通过 X-Api-Key 请求头传入',
            ], 401);
        }

        $limit = (int) $request->query('limit', 24);
        $limit = max(1, min($limit, self::MAX_LIMIT));
        $page = max(1, (int) $request->query('page', 1));
        $sort = $request->query('sort') === 'likes' ? 'likes' : 'upload_at';

        $base = DB::table('textures')
            ->whereIn('type', self::SKIN_TYPES)
            ->where('public', 1);

        $total = (clone $base)->count();

        $rows = $base
            ->orderByDesc($sort)
            ->orderByDesc('tid')
            ->forPage($page, $limit)
            ->get(['tid', 'name', 'hash', 'type', 'likes', 'upload_at']);

        $items = [];
        foreach ($rows as $row) {
            $items[] = [
                'tid' => (int) $row->tid,
                'name' => (string) $row->name,
                'model' => $row->type === 'alex' ? 'slim' : 'classic',
                'url' => secure_url('textures/'.$row->hash),
                'likes' => (int) $row->likes,
                'uploaded_at' => (string) $row->upload_at,
            ];
        }

        return response()
            ->json([
                'page' => $page,
                'limit' => $limit,
                'total' => $total,
                'items' => $items,
            ])
            // 公开数据，允许中间层短缓存，减轻数据库与带宽压力
            ->header('Cache-Control', 'public, max-age=60');
    }

    public function apply(Request $request)
    {
        $user = $this->resolveUser($request);
        if ($user === null) {
            return response()->json([
                'error' => 'unauthorized',
                'message' => 'accessToken 缺失、无效或已过期',
            ], 401);
        }

        $tid = (int) $request->input('tid');
        /** @var Texture|null $texture */
        $texture = Texture::find($tid);
        if (
            $texture === null
            || !in_array($texture->type, self::SKIN_TYPES, true)
            || !$texture->public
        ) {
            return response()->json([
                'error' => 'invalid_texture',
                'message' => '皮肤不存在或未公开',
            ], 404);
        }

        $uuid = strtolower(preg_replace('/[^0-9a-fA-F]/', '', (string) $request->input('uuid', '')));
        $player = null;

        if ($uuid !== '') {
            if (strlen($uuid) !== 32) {
                return response()->json([
                    'error' => 'invalid_uuid',
                    'message' => 'uuid 格式不正确',
                ], 400);
            }

            $mapping = DB::table('uuid')->where('uuid', $uuid)->first();
            if ($mapping !== null) {
                $player = Player::where('uid', $user->uid)
                    ->where('name', $mapping->name)
                    ->first();
            }
        } else {
            $player = Player::where('uid', $user->uid)->orderBy('pid')->first();
        }

        if ($player === null) {
            // 角色不存在与角色属于别人，对外统一返回同一条信息，避免探测他人角色
            return response()->json([
                'error' => 'forbidden',
                'message' => '当前账号下没有该角色',
            ], 403);
        }

        // 与站点自身的换肤逻辑保持一致：皮肤要先在衣柜里才能挂到角色上
        if ($user->closet()->where('texture_tid', $tid)->doesntExist()) {
            DB::table('user_closet')->insert([
                'user_uid' => $user->uid,
                'texture_tid' => $tid,
                'item_name' => null,
            ]);
        }

        event('player.texture.updating', [$player, $texture]);

        $player->tid_skin = $tid;
        $player->last_modified = date('Y-m-d H:i:s');
        $player->save();

        return response()->json([
            'ok' => true,
            'player' => $player->name,
            'uuid' => $uuid,
            'tid' => $tid,
            'model' => $texture->type === 'alex' ? 'slim' : 'classic',
            'skin_url' => secure_url('textures/'.$texture->hash),
        ]);
    }

    /**
     * 从请求里反查账号，支持两种凭据：
     *
     *  1. `Authorization: Bearer vk_xxx`（或 `X-Api-Key: vk_xxx`）—— 开发者页面签发的账号级 Key。
     *     微软/离线账号没有 Yggdrasil token，只能走这条；
     *  2. `Authorization: Bearer <Yggdrasil accessToken>` —— 游戏登录链路签发的 token，
     *     用于外置登录账号，保持兼容。
     */
    /**
     * 取出请求里的 key 记录（应用级或账号级），无效返回 null。
     */
    private function keyRecord(Request $request): ?object
    {
        // 前缀 vk_ 是保留的，不会与 Yggdrasil 的 UUID 型 token 冲突
        $apiKey = $request->header('X-Api-Key');
        $bearer = $request->bearerToken();
        if (empty($apiKey) && !empty($bearer) && str_starts_with((string) $bearer, ApiKey::PREFIX)) {
            $apiKey = $bearer;
        }

        return empty($apiKey) ? null : ApiKey::resolve((string) $apiKey);
    }

    /**
     * 反查「具体账号」，用于换肤这类必须落在某个人名下的操作。
     *
     * 应用级 key（kind=app，uid=0）只代表启动器本身，不映射任何账号，这里返回 null。
     */
    private function resolveUser(Request $request): ?User
    {
        $record = $this->keyRecord($request);
        if ($record !== null) {
            $uid = (int) $record->uid;

            return $uid > 0 ? User::find($uid) : null;
        }

        $bearer = $request->bearerToken();
        if (empty($bearer)) {
            return null;
        }

        $token = Token::find($bearer);
        if ($token === null || !$token->isValid() || empty($token->owner)) {
            return null;
        }

        return User::where('email', $token->owner)->first();
    }

    /**
     * 当前凭据对应的账号与角色列表。
     *
     * 启动器用它在填完 Key 后确认「这个 Key 是谁的、下面有哪些角色」，
     * 避免玩家把皮肤应用到错的账号上。
     */
    public function me(Request $request)
    {
        $record = $this->keyRecord($request);
        if ($record !== null && (int) $record->uid === 0) {
            // 应用级 key：没有角色，但要让开发者能确认这把 key 生效了
            return response()->json([
                'ok' => true,
                'kind' => 'app',
                'uid' => 0,
                'players' => [],
            ]);
        }

        $user = $this->resolveUser($request);
        if ($user === null) {
            return response()->json([
                'error' => 'unauthorized',
                'message' => 'API Key 或 accessToken 缺失、无效或已撤销',
            ], 401);
        }

        $players = Player::where('uid', $user->uid)
            ->orderBy('pid')
            ->get(['name', 'tid_skin', 'tid_cape']);

        $list = [];
        foreach ($players as $player) {
            $uuid = DB::table('uuid')->where('name', $player->name)->value('uuid');
            $list[] = [
                'name' => (string) $player->name,
                'uuid' => $uuid !== null ? (string) $uuid : null,
                'tid_skin' => $player->tid_skin !== null ? (int) $player->tid_skin : null,
                'tid_cape' => $player->tid_cape !== null ? (int) $player->tid_cape : null,
            ];
        }

        return response()->json([
            'ok' => true,
            'kind' => 'account',
            'uid' => (int) $user->uid,
            'players' => $list,
        ]);
    }
}
