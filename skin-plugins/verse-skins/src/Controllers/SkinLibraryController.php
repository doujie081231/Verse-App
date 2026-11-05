<?php

namespace VerseSkins\Controllers;

use App\Models\Player;
use App\Models\Texture;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
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
     * 从 Bearer token 反查账号。
     * token 由 yggdrasil-api 签发，owner 存的是账号邮箱。
     */
    private function resolveUser(Request $request): ?User
    {
        $accessToken = $request->bearerToken();
        if (empty($accessToken)) {
            return null;
        }

        $token = Token::find($accessToken);
        if ($token === null || !$token->isValid() || empty($token->owner)) {
            return null;
        }

        return User::where('email', $token->owner)->first();
    }
}
