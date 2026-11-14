<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * 开发者 API Key 表（含审核状态）。
 *
 * 与 `src/Support/ApiKey::ensureTable()` 的结构保持一致：那边是懒加载兜底
 * （插件可能没在后台执行过迁移），这里是给「插件 → 执行迁移」用的正规入口。
 *
 * 状态机：pending 待审核 → approved 已批准（可自行生成）→ active 已生成 → revoked 已撤销；
 * rejected 为驳回。Key 明文不入库，只存 sha256。
 */
return new class extends Migration
{
    public function up(): void
    {
        if (Schema::hasTable('verse_api_keys')) {
            return;
        }

        Schema::create('verse_api_keys', function (Blueprint $table) {
            $table->increments('kid');
            $table->unsignedInteger('uid')->index();
            $table->string('key_hash', 64)->nullable()->unique();
            $table->string('key_prefix', 16)->nullable();
            $table->string('status', 16)->default('pending');
            $table->text('reason')->nullable();
            $table->unsignedInteger('reviewed_by')->nullable();
            $table->timestamp('reviewed_at')->nullable();
            $table->timestamp('created_at')->nullable();
            $table->timestamp('last_used_at')->nullable();
            $table->timestamp('revoked_at')->nullable();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('verse_api_keys');
    }
};
