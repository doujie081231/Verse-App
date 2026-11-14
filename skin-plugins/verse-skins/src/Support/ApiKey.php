<?php

namespace VerseSkins\Support;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/**
 * 开发者 API Key：账号级凭据，供启动器以「皮肤站账号」身份调用写接口。
 *
 * 为什么要有它：Yggdrasil token 只在游戏登录链路里签发，微软账号、离线账号根本拿不到；
 * 而这些玩家同样想「把库里的皮肤应用到自己站内角色」。
 *
 * 与最初版本的差别：**申请需要管理员审核**。普通用户只能提交申请（写明用途），
 * 管理员在开发者页的审核区批准后，用户才能自己生成 Key —— Key 明文始终只出现在
 * 申请人自己的页面上，管理员看不到任何人的 Key。
 *
 * 状态机：
 *   pending   已提交申请，等待审核
 *   approved  审核通过，可自助生成 Key（此时还没有 key_hash）
 *   active    已生成 Key，可调用接口
 *   rejected  审核未通过（reason 位保存驳回理由）
 *   revoked   已撤销，需重新申请
 */
class ApiKey
{
    public const PREFIX = 'vk_';

    public const TABLE = 'verse_api_keys';

    /** 应用级 Key（开发者接入公开接口用，CurseForge 式的 x-api-key） */
    public const KIND_APP = 'app';

    /** 账号级 Key（代表某个皮肤站账号操作，换肤用） */
    public const KIND_ACCOUNT = 'account';

    public const STATUS_PENDING = 'pending';
    public const STATUS_APPROVED = 'approved';
    public const STATUS_ACTIVE = 'active';
    public const STATUS_REJECTED = 'rejected';
    public const STATUS_REVOKED = 'revoked';

    /** 驳回后再次申请的最小间隔（秒），避免反复提交骚扰审核 */
    public const REAPPLY_COOLDOWN = 86400;

    private static bool $tableReady = false;

    /**
     * 首次使用时确保表结构就绪（含审核相关列）。
     *
     * 皮肤站后台的「插件 → 执行迁移」不一定被执行过，这里做惰性兜底：
     * 建表/补列失败只记日志不抛错，避免整个插件在首页就白屏。
     */
    public static function ensureTable(): void
    {
        if (self::$tableReady) {
            return;
        }
        try {
            if (!Schema::hasTable(self::TABLE)) {
                Schema::create(self::TABLE, function ($table) {
                    $table->increments('kid');
                    $table->unsignedInteger('uid')->index();
                    $table->string('key_hash', 64)->nullable()->unique();
                    $table->string('key_prefix', 16)->nullable();
                    $table->string('kind', 16)->default(self::KIND_ACCOUNT);
                    $table->string('status', 16)->default(self::STATUS_PENDING);
                    $table->text('reason')->nullable();
                    $table->unsignedInteger('reviewed_by')->nullable();
                    $table->timestamp('reviewed_at')->nullable();
                    $table->timestamp('created_at')->nullable();
                    $table->timestamp('last_used_at')->nullable();
                    $table->timestamp('revoked_at')->nullable();
                });
            } else {
                // 老库兼容：从「自助生成」时代升级上来的表没有这几列
                $columns = [
                    'kind' => function ($table) {
                        $table->string('kind', 16)->default(self::KIND_ACCOUNT);
                    },
                    'status' => function ($table) {
                        $table->string('status', 16)->default(self::STATUS_ACTIVE);
                    },
                    'reason' => function ($table) {
                        $table->text('reason')->nullable();
                    },
                    'reviewed_by' => function ($table) {
                        $table->unsignedInteger('reviewed_by')->nullable();
                    },
                    'reviewed_at' => function ($table) {
                        $table->timestamp('reviewed_at')->nullable();
                    },
                ];
                foreach ($columns as $name => $define) {
                    if (!Schema::hasColumn(self::TABLE, $name)) {
                        Schema::table(self::TABLE, function ($table) use ($define) {
                            $define($table);
                        });
                    }
                }
            }
        } catch (\Throwable $e) {
            if (function_exists('logger')) {
                logger()->warning('[verse-skins] 初始化 API Key 表失败：'.$e->getMessage());
            }
        }
        self::$tableReady = true;
    }

    /** 账号当前那条有效记录（最新的、未被彻底作废的） */
    public static function current(int $uid): ?object
    {
        self::ensureTable();

        return DB::table(self::TABLE)
            ->where('uid', $uid)
            ->whereIn('status', [
                self::STATUS_PENDING,
                self::STATUS_APPROVED,
                self::STATUS_ACTIVE,
                self::STATUS_REJECTED,
            ])
            ->orderByDesc('kid')
            ->first();
    }

    /** 提交申请（已有待审核/已开通时拒绝重复提交） */
    public static function apply(int $uid, string $reason): array
    {
        self::ensureTable();

        $existing = self::current($uid);
        if ($existing !== null) {
            if ($existing->status === self::STATUS_REJECTED) {
                $cooled = $existing->created_at !== null
                    && strtotime((string) $existing->created_at) + self::REAPPLY_COOLDOWN < time();
                if (!$cooled) {
                    return ['ok' => false, 'message' => '上次申请未通过，请 24 小时后再试'];
                }
            } else {
                return ['ok' => false, 'message' => '已有申请在处理或已开通，无需重复提交'];
            }
        }

        DB::table(self::TABLE)->insert([
            'uid' => $uid,
            'key_hash' => null,
            'key_prefix' => null,
            'status' => self::STATUS_PENDING,
            'reason' => mb_substr($reason, 0, 200),
            'created_at' => now(),
        ]);

        return ['ok' => true, 'message' => '申请已提交，等待管理员审核'];
    }

    /** 待审核申请（管理员页面用） */
    public static function pendingList(int $limit = 100): array
    {
        self::ensureTable();

        return DB::table(self::TABLE.' as k')
            ->leftJoin('users as u', 'u.uid', '=', 'k.uid')
            ->where('k.status', self::STATUS_PENDING)
            ->orderBy('k.kid')
            ->limit($limit)
            ->get([
                'k.kid',
                'k.uid',
                'k.reason',
                'k.created_at',
                'u.nickname',
                'u.email',
            ])
            ->all();
    }

    /** 审核通过：只授予「可生成」资格，Key 由用户自己生成，管理员看不到明文 */
    public static function approve(int $kid, int $reviewerUid): bool
    {
        self::ensureTable();

        return DB::table(self::TABLE)
            ->where('kid', $kid)
            ->where('status', self::STATUS_PENDING)
            ->update([
                'status' => self::STATUS_APPROVED,
                'reviewed_by' => $reviewerUid,
                'reviewed_at' => now(),
            ]) > 0;
    }

    /** 审核驳回 */
    public static function reject(int $kid, int $reviewerUid, string $note = ''): bool
    {
        self::ensureTable();

        return DB::table(self::TABLE)
            ->where('kid', $kid)
            ->where('status', self::STATUS_PENDING)
            ->update([
                'status' => self::STATUS_REJECTED,
                'reason' => mb_substr($note !== '' ? $note : '未说明理由', 0, 200),
                'reviewed_by' => $reviewerUid,
                'reviewed_at' => now(),
            ]) > 0;
    }

    /**
     * 生成 Key。
     *
     * 普通用户必须处于 approved 状态（管理员批准过）；管理员传 $autoApprove = true，
     * 没有申请记录时就地补一条 approved —— 审核机制是给普通用户防滥用的，
     * 让管理员再走一遍自己的审核没有意义。
     *
     * @return array{plain:string,prefix:string}|null
     */
    public static function issue(int $uid, bool $autoApprove = false): ?array
    {
        self::ensureTable();

        $row = self::current($uid);

        if (
            $autoApprove
            && ($row === null || in_array($row->status, [self::STATUS_REJECTED, self::STATUS_REVOKED], true))
        ) {
            // 管理员首次使用或撤销后再来：直接落一条 approved，随后立即生成
            DB::table(self::TABLE)->insert([
                'uid' => $uid,
                'key_hash' => null,
                'key_prefix' => null,
                'status' => self::STATUS_APPROVED,
                'reason' => '管理员自助生成',
                'reviewed_by' => $uid,
                'reviewed_at' => now(),
                'created_at' => now(),
            ]);
            $row = self::current($uid);
        }

        if ($row === null || $row->status !== self::STATUS_APPROVED) {
            return null;
        }

        $plain = self::PREFIX.bin2hex(random_bytes(24));
        $prefix = substr($plain, 0, 12);

        DB::table(self::TABLE)->where('kid', $row->kid)->update([
            'key_hash' => hash('sha256', $plain),
            'key_prefix' => $prefix,
            'status' => self::STATUS_ACTIVE,
        ]);

        return ['plain' => $plain, 'prefix' => $prefix];
    }

    /** 账号当前唯一可用的 Key 记录（status=active） */
    public static function active(int $uid): ?object
    {
        self::ensureTable();

        return DB::table(self::TABLE)
            ->where('uid', $uid)
            ->where('status', self::STATUS_ACTIVE)
            ->orderByDesc('kid')
            ->first();
    }

    /** 撤销账号下所有可用 Key */
    public static function revokeAll(int $uid): int
    {
        self::ensureTable();

        return DB::table(self::TABLE)
            ->where('uid', $uid)
            ->where('status', self::STATUS_ACTIVE)
            ->update([
                'status' => self::STATUS_REVOKED,
                'revoked_at' => now(),
                'key_hash' => null,
            ]);
    }

    /**
     * 校验明文 key，返回 key 记录（含 uid / kind，顺带刷新 last_used_at）。
     *
     * 失败一律返回 null，不区分「格式不对 / 已撤销 / 不存在」，避免探测。
     * 应用级 key 的 uid 为 0，调用方以此判断它不是某个具体账号。
     */
    public static function resolve(?string $plain): ?object
    {
        if ($plain === null || $plain === '' || !str_starts_with($plain, self::PREFIX)) {
            return null;
        }

        self::ensureTable();

        $row = DB::table(self::TABLE)
            ->where('key_hash', hash('sha256', $plain))
            ->where('status', self::STATUS_ACTIVE)
            ->first();

        if ($row === null) {
            return null;
        }

        try {
            DB::table(self::TABLE)->where('kid', $row->kid)->update(['last_used_at' => now()]);
        } catch (\Throwable $e) {
            // 统计字段写失败不影响鉴权结果
        }

        return $row;
    }

    /**
     * 签发应用级 Key（开发者接入公开接口用）。
     *
     * 只给运维/管理员在服务端调用，不进任何用户可见流程：应用级 key 代表「整个启动器」
     * 而不是某个玩家，泄露后受影响的是配额而不是账号。
     */
    public static function issueApp(string $label = 'Verse 启动器'): array
    {
        self::ensureTable();

        $plain = self::PREFIX.bin2hex(random_bytes(24));
        $prefix = substr($plain, 0, 12);

        DB::table(self::TABLE)->insert([
            'uid' => 0,
            'key_hash' => hash('sha256', $plain),
            'key_prefix' => $prefix,
            'kind' => self::KIND_APP,
            'status' => self::STATUS_ACTIVE,
            'reason' => mb_substr($label, 0, 200),
            'created_at' => now(),
        ]);

        return ['plain' => $plain, 'prefix' => $prefix];
    }
}
