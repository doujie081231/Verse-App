<?php

use App\Services\Hook;
use Blessing\Filter;
use Illuminate\Support\Facades\Route;

return function (Filter $filter) {
    // 开发者页面用 Blade 渲染，先注册视图命名空间；放在回调外层，
    // 让页面请求进来时一定已经能找到视图。
    try {
        app('view')->addNamespace('verse-skins', __DIR__.'/views');
    } catch (\Throwable $e) {
        // 视图门面未就绪时静默跳过，不影响 API 路由
    }

    Hook::addRoute(function () {
        // 皮肤库接口。设计前提：任何启动器都能接，但必须持有一把站点签发的 Key，
        // 这样运维能按 Key 看调用量、限流和撤销。Key 分两种：
        // 应用级（uid=0，只读列表，内置在官方启动器里）与账号级（开发者中心申请）。
        //
        //   GET  /api/verse-skins          需 X-Api-Key，返回公开皮肤（限流 60 次/分钟/IP）
        //   GET  /api/verse-skins/me       查询当前凭据对应的账号与角色（限流 60 次/分钟/IP）
        //   POST /api/verse-skins/apply    应用皮肤，需账号级 Key 或 Yggdrasil accessToken（限流 20 次/分钟/IP）
        //
        // 写操作只允许作用于凭据归属账号名下的角色，避免"拿有效凭据改别人皮肤"的越权。
        //
        // 注意：Blessing Skin 核心自带的 /skinlib/list、/skinlib/info/{id}、/texture/{id}
        // 是网页皮肤库用的公开接口，不在本插件管辖范围内，匿名仍可读取公开皮肤目录。
        // 本插件的 Key 只保护上面这组 /api/verse-skins 接口，别把两者混为一谈。
        Route::prefix('api/verse-skins')
            ->namespace('VerseSkins\Controllers')
            ->middleware(['api'])
            ->group(function () {
                Route::get('/', 'SkinLibraryController@index')
                    ->middleware('throttle:60,1');
                Route::get('me', 'SkinLibraryController@me')
                    ->middleware('throttle:60,1');
                Route::post('apply', 'SkinLibraryController@apply')
                    ->middleware('throttle:20,1');
            });

        // 开发者页面（需登录）：走 web 中间件组，才有 session 与 CSRF。
        //
        // 权限模型：普通用户只能「提交申请」，管理员（users.permission ≥ 2）在页面下方审核；
        // 审核通过后用户才能自己生成 Key —— 明文只出现在申请人页面，管理员拿不到。
        Route::prefix('user/developer')
            ->namespace('VerseSkins\Controllers')
            ->middleware(['web'])
            ->group(function () {
                Route::get('/', 'DeveloperController@index');
                Route::post('apply', 'DeveloperController@apply')
                    ->middleware('throttle:10,1');
                Route::post('api-key', 'DeveloperController@issue')
                    ->middleware('throttle:10,1');
                Route::post('api-key/revoke', 'DeveloperController@revoke')
                    ->middleware('throttle:10,1');
                Route::post('review/approve', 'DeveloperController@approve')
                    ->middleware('throttle:30,1');
                Route::post('review/reject', 'DeveloperController@reject')
                    ->middleware('throttle:30,1');
            });
    });

    // 注意：站点顶栏（Homepage / Skin Library / 使用教程 那一条）是 Twig 模板直出的
    // （resources/views/shared/header.twig），**不走** Hook 菜单钩子 —— 要改顶栏请改模板。
    // 下面两个 addMenuItem 只作用于服务端渲染的侧栏（SideMenuComposer，后台用）。
    // 签名以 app/Services/Hook.php 为准：addMenuItem(category, position, [title, link, icon, new-tab])
    try {
        Hook::addMenuItem('user', 0, [
            'title' => '开发者中心',
            'link' => 'user/developer',
            'icon' => 'fa-code',
            'new-tab' => false,
        ]);
        Hook::addMenuItem('admin', 0, [
            'title' => '开发者中心',
            'link' => 'user/developer',
            'icon' => 'fa-code',
            'new-tab' => false,
        ]);
    } catch (\Throwable $e) {
        // 菜单非必需路径：加不上时仍可直接访问 /user/developer
    }
};
